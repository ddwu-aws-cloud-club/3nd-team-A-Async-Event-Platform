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

    // (옵션) messageAttributes에서 복구할 때 사용할 키(ingest가 messageAttributes로도 넣는다면)
    private static final String MA_REQUEST_ID = "requestId";
    private static final String MA_EVENT_ID   = "eventId";
    private static final String MA_USER_ID    = "userId";
    private static final String MA_EVENT_TYPE = "eventType";
    private static final String MA_QUEUED_AT  = "queuedAt";

    @Scheduled(fixedDelay = 3000)
    public void pollMessages() {
        ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(5)
                .attributeNamesWithStrings("ApproximateReceiveCount")                .messageAttributeNames("All")
                .build();

        List<Message> messages = sqsClient.receiveMessage(req).messages();
        for (Message m : messages) {
            handleMessage(m);
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
                log.info("[SKIP/INVALID] requestId={} already status={}, attempt={}",
                        payload.requestId(), cur.orElse("UNKNOWN"), attempt);
                deleteMessage(message);
                return;
            }

            // 그 다음에 PROCESSING -> FAILED_FINAL
            long finishedAt = System.currentTimeMillis();
            boolean updated = requestStateRepository.markFailedFinal(
                    payload.requestId(),
                    finishedAt,
                    ResultCode.FAILED_INGEST_ENQUEUE, // 아래 2)에서 enum 추천
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

        // 3) QUEUED -> PROCESSING 선점
        boolean acquired = requestStateRepository.tryAcquireProcessing(payload.requestId(), startedAt);
        if (!acquired) {
            Optional<String> cur = requestStateRepository.getCurrentStatus(payload.requestId());

            if (cur.isEmpty()) {
                // RequestItem 없음 = 유령 메시지 -> ack ✅
                log.info("[SKIP] requestId={} no item found, attempt={} (ghost msg -> ack)", payload.requestId(), attempt);
                deleteMessage(message);
                return;
            }

            String status = cur.get();
            switch (status) {
                case "RECEIVED":
                case "QUEUED":
                    // 레이스/경합 -> ack ❌ (재시도에서 다시 잡도록)
                    log.info("[SKIP] requestId={} status={} attempt={} (race/contend -> ack)", payload.requestId(), status, attempt);
                    long finishedAt = System.currentTimeMillis();
                    publisher.publish(buildEvent(
                            payload, attempt, IS_DLQ,
                            startedAt, finishedAt,
                            RequestStatus.REJECTED, // 혹은 status에 맞춰서 변환
                            ResultCode.DUPLICATE_SKIPPED,      // 혹은 DUPLICATE_SKIPPED
                            null,
                            true //  isDuplicate = true
                    ));
                    deleteMessage(message); // RECEIVED 에서도 재시도 루프 끊도록
                    return;
                case "PROCESSING":
                case "SUCCEEDED":
                case "REJECTED":
                case "FAILED_FINAL":
                default:
                    // 이미 처리 중/처리 완료/알 수 없음 -> 중복 메시지로 보고 ack
                    log.info("[SKIP] requestId={} status={} attempt={} (dup -> ack)", payload.requestId(), status, attempt);
                    // 중복 방어 이벤트 발행 (S3 로그용)
                    finishedAt = System.currentTimeMillis();
                    publisher.publish(buildEvent(
                            payload, attempt, IS_DLQ,
                            startedAt, finishedAt,
                            RequestStatus.SUCCEEDED, // 혹은 status에 맞춰서 변환
                            ResultCode.SUCCESS,      // 혹은 DUPLICATE_SKIPPED
                            null,
                            true //  isDuplicate = true
                    ));
                    deleteMessage(message);
                    return;
            }
        }

        // 4) 단일 Worker만 진입
        try {
            final long finishedAt;
            final RequestStatus finalStatus;
            final ResultCode resultCode;

            if (payload.eventType() == EventType.FIRST_COME) {
                boolean gotSlot = eventCapacityRepository.tryDecrement(payload.eventId());
                finishedAt = System.currentTimeMillis();

                if (gotSlot) {
                    boolean ok = requestStateRepository.markSucceeded(payload.requestId(), finishedAt);
                    log.info("[FINAL] requestId={} -> SUCCEEDED (updated={}) attempt={}", payload.requestId(), ok, attempt);
                    finalStatus = RequestStatus.SUCCEEDED;
                    resultCode = ResultCode.SUCCESS;
                } else {
                    boolean ok = requestStateRepository.markRejectedCapacity(payload.requestId(), finishedAt);
                    log.info("[FINAL] requestId={} -> REJECTED_CAPACITY (updated={}) attempt={}", payload.requestId(), ok, attempt);
                    finalStatus = RequestStatus.REJECTED;
                    resultCode = ResultCode.REJECTED_CAPACITY;
                }
            } else {
                finishedAt = System.currentTimeMillis();
                boolean ok = requestStateRepository.markSucceeded(payload.requestId(), finishedAt);
                log.info("[FINAL] requestId={} -> SUCCEEDED (non-FIRST_COME, updated={}) attempt={}", payload.requestId(), ok, attempt);
                finalStatus = RequestStatus.SUCCEEDED;
                resultCode = ResultCode.SUCCESS;
            }

            publisher.publish(buildEvent(payload, attempt, IS_DLQ, startedAt, finishedAt, finalStatus, resultCode, null, false));
            deleteMessage(message);

        } catch (Exception e) {
            FailureClass cls = classifyFailure(e);

            if (cls == FailureClass.NON_RETRYABLE) {
                long finishedAt = System.currentTimeMillis();

                boolean ok = requestStateRepository.markFailedFinal(
                        payload.requestId(),
                        finishedAt,
                        ResultCode.FAILED_INGEST_ENQUEUE, // 권장: FAILED_WORKER_EXCEPTION 같은 enum 추가
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

                log.warn("[NON-RETRYABLE] requestId={} -> FAILED_FINAL updated={} attempt={}", payload.requestId(), ok, attempt, e);
                deleteMessage(message);
                return;
            }

            // Retryable: ack 하지 않음 -> 재시도 -> DLQ
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
