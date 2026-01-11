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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors; // ★ [수정] 내부 병렬화용

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RequestStateRepository requestStateRepository;
    private final EventCapacityRepository eventCapacityRepository;
    private final ParticipationEventBridgePublisher publisher;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    // 나중에 yml + @Value로 바꾸기
    private static final int SCHEMA_VERSION = 1;
    private static final String ENV = "dev";

    // DLQ consumer 분리 시 true로 두고 재사용 가능
    private static final boolean IS_DLQ = false;

    // ★ [수정] RECEIVED 상태에서 바로 ACK하지 않고, 잠깐 뒤 재시도하기 위한 visibility delay
    private static final int VISIBILITY_DELAY_SECONDS = 2;

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
        ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(5)
                .attributeNamesWithStrings("ApproximateReceiveCount")
                .messageAttributeNames("All")
                .build();

        List<Message> messages = sqsClient.receiveMessage(req).messages();

        for (Message m : messages) {
            // =========================================================
            // ★ [수정] 기존: handleMessage(m);
            // → 처리 단계만 ThreadPool에 위임
            // =========================================================
            executor.submit(() -> handleMessage(m));
        }
    }

    private void handleMessage(Message message) {
        log.info(">>> Worker 받음! Body={}", message.body());

        final int attempt = parseAttempt(message);
        final long startedAt = System.currentTimeMillis();

        ParticipationMessage payload;

        // 1) body 파싱 시도
        try {
            payload = objectMapper.readValue(message.body(), ParticipationMessage.class);
            log.info("[PARSED OK/BODY] requestId={}, eventId={}, userId={}, eventType={}, queuedAt={}, attempt={}",
                    payload.requestId(), payload.eventId(), payload.userId(), payload.eventType(), payload.queuedAt(), attempt);

        } catch (Exception bodyEx) {
            // 2) body가 깨졌으면 messageAttributes로 복구 시도(선택)
            try {
                payload = buildPayloadFromAttributes(message.messageAttributes());
                log.warn("[PARSED OK/ATTR] body invalid -> recovered. requestId={}, eventId={}, userId={}, eventType={}, queuedAt={}, attempt={}",
                        payload.requestId(), payload.eventId(), payload.userId(), payload.eventType(), payload.queuedAt(), attempt);
            } catch (Exception attrEx) {
                // requestId조차 없으면 전이 불가 -> ack
                log.warn("[NON-RETRYABLE] invalid body and cannot recover attrs. attempt={} body={}",
                        attempt, safeBody(message), bodyEx);
                deleteMessage(message);
                return;
            }

            // QUEUED -> PROCESSING 선점 먼저
            boolean acquired = requestStateRepository.tryAcquireProcessing(payload.requestId(), startedAt);
            if (!acquired) {
                Optional<String> cur = requestStateRepository.getCurrentStatus(payload.requestId());
                String status = cur.orElse("UNKNOWN");
                if ("RECEIVED".equals(status)) {
                    log.info("[DEFER/INVALID] requestId={} status={} attempt={} (wait ingest -> retry)", payload.requestId(), status, attempt);
                    deferMessage(message, VISIBILITY_DELAY_SECONDS);
                    return;
                }
                log.info("[SKIP/INVALID] requestId={} already status={}, attempt={}",
                        payload.requestId(), status, attempt);
                deleteMessage(message);
                return;
            }

            // 그 다음에 PROCESSING -> FAILED_FINAL
            long finishedAt = System.currentTimeMillis();
            boolean updated = requestStateRepository.markFailedFinal(
                    payload.requestId(),
                    finishedAt,
                    ResultCode.FAILED_INGEST_ENQUEUE,
                    FailureClass.NON_RETRYABLE,
                    "INVALID_MESSAGE_BODY",
                    "Body JSON parse failed; recovered from messageAttributes"
            );

            publisher.publish(buildEvent(
                    payload, attempt, IS_DLQ,
                    startedAt, finishedAt,
                    RequestStatus.FAILED_FINAL,
                    ResultCode.FAILED_INGEST_ENQUEUE,
                    new ParticipationProcessedFailure(FailureClass.NON_RETRYABLE, "INVALID_MESSAGE_BODY", "Body JSON parse failed"),
                    false
            ));

            log.warn("[FAILED_FINAL] requestId={} updated={} attempt={} (INVALID_MESSAGE_BODY)",
                    payload.requestId(), updated, attempt);

            deleteMessage(message);
            return;
        }

        // === 이하 로직 전부 기존 그대로 ===
        // (QUEUED -> PROCESSING / FINAL 처리 / 실패 처리 등)
        // ※ 변경 없음
        // ------------------------------------------------------------

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
                    log.info("[DEFER] requestId={} status={} attempt={} (wait ingest -> retry)", payload.requestId(), status, attempt);
                    deferMessage(message, VISIBILITY_DELAY_SECONDS);
                    return;
                case "QUEUED":
                    log.info("[SKIP] requestId={} status={} attempt={} (race/contend -> ack)", payload.requestId(), status, attempt);
                    long finishedAt = System.currentTimeMillis();
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

        // ✅ [추가 로그 1] PROCESSING 선점 성공
        log.info("[ACQUIRED] requestId={} attempt={} startedAt={}",
                payload.requestId(), attempt, startedAt);

        try {
            final long finishedAt;
            final RequestStatus finalStatus;
            final ResultCode resultCode;

            if (payload.eventType() == EventType.FIRST_COME) {
                boolean gotSlot = eventCapacityRepository.tryDecrement(payload.eventId());

                // ✅ [추가 로그 2] Capacity 결과
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

            log.error("[RETRYABLE] worker exception requestId={} attempt={}", payload.requestId(), attempt, e);
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

        // ✅ record 시그니처 순서: requestId, eventId, userId, queuedAt, eventType
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
}
