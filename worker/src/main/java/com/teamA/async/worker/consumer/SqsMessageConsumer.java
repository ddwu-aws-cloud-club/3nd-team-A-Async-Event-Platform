package com.teamA.async.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.FailureClass;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.worker.analytics.event.*;
import com.teamA.async.worker.analytics.publisher.ParticipationEventBridgePublisher;
import com.teamA.async.worker.ddb.EventCapacityRepository;
import com.teamA.async.worker.ddb.RequestStateRepository;
import com.teamA.async.worker.ddb.WorkerIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RequestStateRepository requestStateRepository;
    private final EventCapacityRepository eventCapacityRepository;
    private final ParticipationEventBridgePublisher publisher;
    private final WorkerIdempotencyRepository idempotencyRepository;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    // 나중에 yml + @Value로 바꾸기
    private static final int SCHEMA_VERSION = 1;
    private static final String ENV = "dev";

    // DLQ consumer 분리 시 true로 두고 재사용 가능
    private static final boolean IS_DLQ = false;

    // ★ [수정] RECEIVED 상태에서 바로 ACK하지 않고, 잠깐 뒤 재시도하기 위한 visibility delay
    private static final int VISIBILITY_DELAY_SECONDS = 2;

    // ✅ [수정] RECEIVED 무한 defer 방지용 상한 (attempt 기준)
    private static final int MAX_RECEIVED_RETRY = 3;

    // ✅ [수정] RETRYABLE 무한 재등장 방지용 상한 (attempt 기준)
    private static final int MAX_RETRYABLE_RETRY = 5;

    // ✅ [수정] RETRYABLE backoff (최대 60초)
    private static final int RETRYABLE_BACKOFF_BASE_SECONDS = 2;
    private static final int RETRYABLE_BACKOFF_MAX_SECONDS = 60;

    // (옵션) messageAttributes에서 복구할 때 사용할 키(ingest가 messageAttributes로도 넣는다면)
    private static final String MA_REQUEST_ID = "requestId";
    private static final String MA_EVENT_ID   = "eventId";
    private static final String MA_USER_ID    = "userId";
    private static final String MA_EVENT_TYPE = "eventType";
    private static final String MA_QUEUED_AT  = "queuedAt";

    // =========================================================
    // ★ [수정] 제한적 내부 병렬화용 고정 ThreadPool
    // - poller는 단일
    // - 메시지 처리(handleMessage)만 병렬
    // =========================================================
    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    @Scheduled(fixedDelay = 3000)
    public void pollMessages() {

        try {
            ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .waitTimeSeconds(5) // 응답을 기다리는 시간
                    .maxNumberOfMessages(5) // 한 번에 가져올 메시지 수
                    .attributeNamesWithStrings("ApproximateReceiveCount")
                    .messageAttributeNames("All")
                    .build();

            // SQS로부터 메시지 수신
            List<Message> messages = sqsClient.receiveMessage(req).messages();
            log.info("[POLL] receivedCount={}", messages.size());

            for (Message m : messages) {
                // 이제 안전하게 스레드 풀로 넘깁니다.
                executor.submit(() -> handleMessage(m));
            }

        } catch (Exception e) {
            // 인증 오류(Credentials), 리전 오류, 네트워크 오류 등이 여기서 출력됩니다.
            log.error("[SQS ERROR] poll failed", e);
        }
    }

    private void handleMessage(Message message) {
        log.info(">>> Worker 받음! Body={}", message.body());

        final int attempt = parseAttempt(message);
        final long startedAt = System.currentTimeMillis();

        ParticipationMessage payload;

        // payload 확보
        try {
            payload = objectMapper.readValue(message.body(), ParticipationMessage.class);
            log.info("[PARSED OK/BODY] requestId={}, eventId={}, userId={}, eventType={}, queuedAt={}, attempt={}",
                    payload.requestId(), payload.eventId(), payload.userId(), payload.eventType(), payload.queuedAt(), attempt);

        } catch (Exception bodyEx) {
            try {
                payload = buildPayloadFromAttributes(message.messageAttributes());
                log.warn("[PARSED OK/ATTR] body invalid -> recovered. requestId={}, eventId={}, userId={}, eventType={}, queuedAt={}, attempt={}",
                        payload.requestId(), payload.eventId(), payload.userId(), payload.eventType(), payload.queuedAt(), attempt);
            } catch (Exception attrEx) {
                log.warn("[NON-RETRYABLE] invalid body and cannot recover attrs. attempt={} body={}",
                        attempt, safeBody(message), bodyEx);
                deleteMessage(message);
                return;
            }
        }

        // Worker 책임 — 항상 실행
        boolean first = idempotencyRepository.tryLock(
                payload.eventId(),
                payload.userId(),
                payload.requestId()
        );

        if (!first) {
            log.info("[DUPLICATE] skip requestId={}", payload.requestId());
            deleteMessage(message);
            return;
        }

        log.info("[LOCKED] requestId={} eventId={} userId={}",
                payload.requestId(),
                payload.eventId(),
                payload.userId());

        // 최초 상태 생성
        requestStateRepository.createReceived(
                payload.requestId(),
                payload.eventId(),
                payload.userId(),
                payload.eventType(),
                payload.queuedAt()
        );

        // QUEUED 기록
        requestStateRepository.markQueued(
                payload.requestId(),
                payload.queuedAt()
        );

        // 3) QUEUED -> PROCESSING 선점
        boolean acquired = requestStateRepository.tryAcquireProcessing(payload.requestId(), startedAt);
        if (!acquired) {
            Optional<String> cur = requestStateRepository.getCurrentStatus(payload.requestId());

            if (cur.isEmpty()) {
                log.info("[SKIP] requestId={} no item found, attempt={} (ghost msg -> ack)", payload.requestId(), attempt);
                deleteMessage(message);
                return;
            }

            String status = cur.get();
            switch (status) {
                case "RECEIVED":
                    // [수정] RECEIVED 무한 defer 방지
                    if (attempt <= MAX_RECEIVED_RETRY) {
                        log.info("[DEFER] requestId={} status={} attempt={} (wait ingest -> retry)", payload.requestId(), status, attempt);
                        deferMessage(message, VISIBILITY_DELAY_SECONDS);
                        return;
                    }

                    long finishedAt = System.currentTimeMillis();
                    boolean updated = requestStateRepository.markFailedFinal(
                            payload.requestId(),
                            finishedAt,
                            ResultCode.FAILED_INGEST_ENQUEUE,
                            FailureClass.NON_RETRYABLE,
                            "STALE_RECEIVED",
                            "Request stuck in RECEIVED state"
                    );

                    publisher.publish(buildEvent(
                            payload, attempt, IS_DLQ,
                            startedAt, finishedAt,
                            RequestStatus.FAILED_FINAL,
                            ResultCode.FAILED_INGEST_ENQUEUE,
                            new ParticipationProcessedFailure(FailureClass.NON_RETRYABLE, "STALE_RECEIVED", "Exceeded RECEIVED retry limit"),
                            false
                    ));

                    log.warn("[FAILED_FINAL] requestId={} updated={} attempt={} (STALE_RECEIVED)",
                            payload.requestId(), updated, attempt);

                    deleteMessage(message);
                    return;
                case "QUEUED":
                    log.info("[SKIP] requestId={} status={} attempt={} (race/contend -> ack)", payload.requestId(), status, attempt);
                    finishedAt = System.currentTimeMillis();
                    publisher.publish(buildEvent(
                            payload, attempt, IS_DLQ,
                            startedAt, finishedAt,
                            RequestStatus.REJECTED,
                            ResultCode.DUPLICATE_SKIPPED,
                            null,
                            true
                    ));
                    deleteMessage(message);
                    return;
                default:
                    log.info("[SKIP] requestId={} status={} attempt={} (dup -> ack)", payload.requestId(), status, attempt);
                    finishedAt = System.currentTimeMillis();
                    publisher.publish(buildEvent(
                            payload, attempt, IS_DLQ,
                            startedAt, finishedAt,
                            RequestStatus.SUCCEEDED,
                            ResultCode.SUCCESS,
                            null,
                            true
                    ));
                    deleteMessage(message);
                    return;
            }
        }

        // [추가 로그 1] PROCESSING 선점 성공
        log.info("[ACQUIRED] requestId={} attempt={} startedAt={}",
                payload.requestId(), attempt, startedAt);

        try {
            final long finishedAt;
            final RequestStatus finalStatus;
            final ResultCode resultCode;

            if (payload.eventType() == EventType.FIRST_COME) {
                boolean gotSlot = eventCapacityRepository.tryDecrement(payload.eventId());

                // [추가 로그 2] Capacity 결과
                log.info("[CAPACITY] eventId={} requestId={} gotSlot={}",
                        payload.eventId(), payload.requestId(), gotSlot);

                finishedAt = System.currentTimeMillis();

                if (gotSlot) {
                    requestStateRepository.markSucceeded(payload.requestId(), finishedAt);
                    finalStatus = RequestStatus.SUCCEEDED;
                    resultCode = ResultCode.SUCCESS;
                } else {
                    requestStateRepository.markRejectedCapacity(payload.requestId(), finishedAt);
                    finalStatus = RequestStatus.REJECTED;
                    resultCode = ResultCode.REJECTED_CAPACITY;
                }
            } else {
                finishedAt = System.currentTimeMillis();
                requestStateRepository.markSucceeded(payload.requestId(), finishedAt);
                finalStatus = RequestStatus.SUCCEEDED;
                resultCode = ResultCode.SUCCESS;
            }

            publisher.publish(buildEvent(payload, attempt, IS_DLQ, startedAt, finishedAt, finalStatus, resultCode, null, false));
            deleteMessage(message);

        } catch (Exception e) {
            FailureClass cls = classifyFailure(e);

            if (cls == FailureClass.NON_RETRYABLE) {
                long finishedAt = System.currentTimeMillis();
                requestStateRepository.markFailedFinal(
                        payload.requestId(),
                        finishedAt,
                        ResultCode.FAILED_INGEST_ENQUEUE,
                        FailureClass.NON_RETRYABLE,
                        "WORKER_NON_RETRYABLE",
                        e.getMessage()
                );

                publisher.publish(buildEvent(
                        payload, attempt, IS_DLQ,
                        startedAt, finishedAt,
                        RequestStatus.FAILED_FINAL,
                        ResultCode.FAILED_INGEST_ENQUEUE,
                        new ParticipationProcessedFailure(FailureClass.NON_RETRYABLE, "WORKER_NON_RETRYABLE", e.getMessage()),
                        false
                ));

                deleteMessage(message);
                return;
            }

            // [수정] RETRYABLE 무한 재등장 방지
            if (attempt <= MAX_RETRYABLE_RETRY) {
                int backoff = computeRetryableBackoffSeconds(attempt);
                log.error("[RETRYABLE/DEFER] requestId={} attempt={} backoff={}s", payload.requestId(), attempt, backoff, e);
                deferMessage(message, backoff);
                return;
            }

            long finishedAt = System.currentTimeMillis();
            boolean updated = requestStateRepository.markFailedFinal(
                    payload.requestId(),
                    finishedAt,
                    ResultCode.FAILED_INGEST_ENQUEUE,
                    FailureClass.RETRYABLE,
                    "RETRYABLE_EXHAUSTED",
                    "Exceeded retryable retry limit: " + e.getClass().getSimpleName()
            );

            publisher.publish(buildEvent(
                    payload, attempt, IS_DLQ,
                    startedAt, finishedAt,
                    RequestStatus.FAILED_FINAL,
                    ResultCode.FAILED_INGEST_ENQUEUE,
                    new ParticipationProcessedFailure(FailureClass.RETRYABLE, "RETRYABLE_EXHAUSTED", e.getMessage()),
                    false
            ));

            log.warn("[FAILED_FINAL] requestId={} updated={} attempt={} (RETRYABLE_EXHAUSTED)",
                    payload.requestId(), updated, attempt);

            deleteMessage(message);
        }
    }

    // ---------------- helpers ----------------

    private ParticipationMessage buildPayloadFromAttributes(Map<String, MessageAttributeValue> attrs) {
        String requestId = getAttrString(attrs, MA_REQUEST_ID);
        String eventId   = getAttrString(attrs, MA_EVENT_ID);
        String userId    = getAttrString(attrs, MA_USER_ID);
        String eventTypeStr = getAttrString(attrs, MA_EVENT_TYPE);
        String queuedAtStr  = getAttrString(attrs, MA_QUEUED_AT);

        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("missing requestId attr");
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("missing eventId attr");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("missing userId attr");
        if (eventTypeStr == null || eventTypeStr.isBlank()) throw new IllegalArgumentException("missing eventType attr");
        if (queuedAtStr == null || queuedAtStr.isBlank()) throw new IllegalArgumentException("missing queuedAt attr");

        EventType eventType = EventType.valueOf(eventTypeStr);
        long queuedAt = Long.parseLong(queuedAtStr);

        // record 시그니처 순서: requestId, eventId, userId, queuedAt, eventType
        return new ParticipationMessage(requestId, eventId, userId, queuedAt, eventType);
    }

    private String getAttrString(Map<String, MessageAttributeValue> attrs, String key) {
        MessageAttributeValue v = attrs.get(key);
        return v == null ? null : v.stringValue();
    }

    private int parseAttempt(Message message) {
        try {
            String rc = message.attributesAsStrings().get("ApproximateReceiveCount");
            if (rc == null) return 1;
            return Math.max(1, Integer.parseInt(rc));
        } catch (Exception ignore) {
            return 1;
        }
    }

    private String safeBody(Message message) {
        try {
            String b = message.body();
            if (b == null) return "null";
            return b.length() <= 500 ? b : b.substring(0, 500) + "...";
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private FailureClass classifyFailure(Exception e) {
        if (e instanceof IllegalArgumentException) return FailureClass.NON_RETRYABLE;

        if (e instanceof SqsException se) {
            int sc = se.statusCode();
            if (sc >= 400 && sc < 500) return FailureClass.NON_RETRYABLE;
        }

        if (e instanceof DynamoDbException de) {
            int sc = de.statusCode();
            if (sc >= 400 && sc < 500) return FailureClass.NON_RETRYABLE;
        }

        return FailureClass.RETRYABLE;
    }

    private ParticipationProcessedEvent buildEvent(
            ParticipationMessage payload,
            int attempt,
            boolean isDlq,
            long startedAt,
            long finishedAt,
            RequestStatus finalStatus,
            ResultCode resultCode,
            ParticipationProcessedFailure failureOrNull,
            boolean isDuplicate
    ) {
        ParticipationProcessedTimestamps ts =
                new ParticipationProcessedTimestamps(payload.queuedAt(), startedAt, finishedAt);

        ParticipationProcessedDelivery delivery =
                new ParticipationProcessedDelivery(attempt, isDlq);

        String workerId = Optional.ofNullable(System.getenv("HOSTNAME")).orElse("worker-local");
        ParticipationProcessedMeta meta = new ParticipationProcessedMeta(workerId, isDuplicate);
        if (failureOrNull == null) {
            return ParticipationProcessedEvent.noFailure(
                    SCHEMA_VERSION, ENV,
                    payload.eventId(), payload.requestId(), payload.userId(),
                    payload.eventType(), finalStatus, resultCode,
                    ts, delivery, meta
            );
        }

        return new ParticipationProcessedEvent(
                SCHEMA_VERSION, ENV,
                payload.eventId(), payload.requestId(), payload.userId(),
                payload.eventType(), finalStatus, resultCode,
                ts, delivery, failureOrNull, meta
        );
    }

    private void deferMessage(Message message, int delaySeconds) {
        try {
            sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeout(Math.max(0, delaySeconds))
                    .build());
        } catch (Exception e) {
            // visibility 변경 실패 시에는 안전하게 재시도를 유도하기 위해 ACK하지 않는다.
            log.warn("[DEFER FAIL] changeMessageVisibility failed", e);
        }
    }

    private void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.warn("[ACK FAIL] deleteMessage failed", e);
        }
    }

    // [수정] RETRYABLE backoff 계산 (2,4,8,16... 최대 60초)
    private int computeRetryableBackoffSeconds(int attempt) {
        int exp = Math.max(0, attempt - 1);
        long backoff = (long) RETRYABLE_BACKOFF_BASE_SECONDS << exp;
        if (backoff > RETRYABLE_BACKOFF_MAX_SECONDS) backoff = RETRYABLE_BACKOFF_MAX_SECONDS;
        return (int) backoff;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down worker executor...");
        executor.shutdown();
    }

}
