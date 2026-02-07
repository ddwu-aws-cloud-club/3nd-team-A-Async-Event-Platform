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
import com.teamA.async.worker.dlq.DlqSender;
import com.teamA.async.worker.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.Counter; //dlq 전략수정
import io.micrometer.core.instrument.MeterRegistry; //dlq 전략수정

import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct; //dlq 전략수정
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//dlq전략수정 : 네트워크 timeout/일시 장애 Retryable 명시용 import (JDK 표준이라 컴파일 안전)
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

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

    //dlq 전략수정
    private final DlqSender dlqSender;
    private final MeterRegistry meterRegistry;
    private Counter duplicateSkipCounter;
    private Counter nonRetryableDlqCounter;
    private Counter retryableExceptionCounter; //dlq 전략수정

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

    @PostConstruct //dlq 전략 수정
    public void initMetrics() {
        this.duplicateSkipCounter = Counter.builder("duplicate_skip_count")
                .description("Duplicate requests skipped by idempotency lock")
                .register(meterRegistry);

        // 기본 카운터(전체 합)도 하나 두면 운영이 편함
        this.nonRetryableDlqCounter = Counter.builder("non_retryable_dlq_count")
                .description("Non-retryable messages sent to DLQ")
                .register(meterRegistry);

        this.retryableExceptionCounter = Counter.builder("retryable_exception_count") //dlq 전략수정
                .description("Retryable exceptions while processing messages") //dlq 전략수정
                .register(meterRegistry); //dlq 전략수정
    }


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

                String nonRetryableReasonCode = "INVALID_SCHEMA"; //dlq전략수정 : undefined symbol 해결 + reasonCode 고정

                if (nonRetryableDlqCounter != null) nonRetryableDlqCounter.increment();
                meterRegistry.counter("non_retryable_dlq_count", "reasonCode", nonRetryableReasonCode)
                        .increment();

                log.error("[DLQ SEND] reasonCode={} requestId=? eventId=? userId=? attempt={} messageId={}",
                        nonRetryableReasonCode, attempt, message.messageId());

                // dlq전략수정 : 즉시 DLQ + ACK
                dlqSender.send(
                        safeBody(message),
                        message.messageId(),
                        attempt,
                        "NON_RETRYABLE",
                        nonRetryableReasonCode,
                        Map.of()
                );

                deleteMessage(message);
                return;
            }
        }

        // ================================
        // ✅ [수정] 상태 생성/전이까지 포함해서
        //    "분류 try/catch" 아래로 통일
        // ================================
        try {

            // Worker 책임 — 항상 실행
            boolean first = idempotencyRepository.tryLock(
                    payload.eventId(),
                    payload.userId(),
                    payload.requestId()
            );

            if (!first) {
                log.info("[DUPLICATE] skip requestId={}", payload.requestId());
                if (duplicateSkipCounter != null) duplicateSkipCounter.increment(); //dlq 전략수정
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

                        String nonRetryableReasonCode = "STALE_RECEIVED"; //dlq 전략수정 : undefined symbol 해결

                        if (nonRetryableDlqCounter != null) nonRetryableDlqCounter.increment();
                        meterRegistry.counter("non_retryable_dlq_count", "reasonCode", nonRetryableReasonCode)
                                .increment();

                        log.error("[DLQ SEND] reasonCode={} requestId={} eventId={} userId={} attempt={} messageId={}",
                                nonRetryableReasonCode, payload.requestId(), payload.eventId(), payload.userId(), attempt, message.messageId());

                        // dlq전략수정 : NonRetryable은 즉시 DLQ 전송 + ACK (send 실패 시 delete까지 가지 않아서 재시도됨)
                        dlqSender.send(
                                message.body(),
                                message.messageId(),
                                attempt,
                                "NON_RETRYABLE",
                                nonRetryableReasonCode,
                                Map.of(
                                        "requestId", payload.requestId(),
                                        "eventId", payload.eventId(),
                                        "userId", payload.userId()
                                )
                        );

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
                    case "PROCESSING":
                        // 다른 워커가 처리 중이거나, 이전 시도에서 visibility timeout으로 재수신된 케이스
                        // ✅ 바로 ACK하면 유실/오판 위험. 잠깐 defer로 밀어줌.
                        if (attempt <= MAX_RETRYABLE_RETRY) {
                            log.info("[DEFER] requestId={} status=PROCESSING attempt={} (in-flight -> retry later)",
                                    payload.requestId(), attempt);
                            deferMessage(message, VISIBILITY_DELAY_SECONDS);
                            return;
                        }

                        // ✅ 너무 오래 PROCESSING이면 "의미 있는 실패"로 보고 즉시 DLQ + ACK (선택적으로)
                        long finishedAt2 = System.currentTimeMillis();
                        requestStateRepository.markFailedFinal(
                                payload.requestId(),
                                finishedAt2,
                                ResultCode.FAILED_WORKER_EXCEPTION,
                                FailureClass.NON_RETRYABLE,
                                "STALE_PROCESSING",
                                "Request stuck in PROCESSING state"
                        );

                        String nonRetryableReasonCode2 = "STALE_PROCESSING"; //dlq 전략수정 : undefined symbol 해결

                        if (nonRetryableDlqCounter != null) nonRetryableDlqCounter.increment();
                        meterRegistry.counter("non_retryable_dlq_count", "reasonCode", nonRetryableReasonCode2)
                                .increment();

                        log.error("[DLQ SEND] reasonCode={} requestId={} eventId={} userId={} attempt={} messageId={}",
                                nonRetryableReasonCode2, payload.requestId(), payload.eventId(), payload.userId(), attempt, message.messageId());

                        // dlq전략수정 : NonRetryable은 즉시 DLQ 전송 + ACK
                        dlqSender.send(
                                message.body(),
                                message.messageId(),
                                attempt,
                                "NON_RETRYABLE",
                                nonRetryableReasonCode2,
                                Map.of(
                                        "requestId", payload.requestId(),
                                        "eventId", payload.eventId(),
                                        "userId", payload.userId()
                                )
                        );
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

                //dlq전략수정 : 비즈니스 규칙 위반은 reasonCode를 그대로 DLQ/상태에 박아 운영 분류를 1분 내로 만든다
                String nonRetryableReasonCode = "WORKER_NON_RETRYABLE";
                if (e instanceof BusinessRuleViolationException) {
                    BusinessRuleViolationException brve = (BusinessRuleViolationException) e;
                    nonRetryableReasonCode = brve.getReasonCode();
                }

                long finishedAt = System.currentTimeMillis();
                requestStateRepository.markFailedFinal(
                        payload.requestId(),
                        finishedAt,
                        ResultCode.FAILED_WORKER_EXCEPTION, // dlq 전략수정 : Worker에서 난 예외는 WORKER_EXCEPTION이 더 정확
                        FailureClass.NON_RETRYABLE,
                        nonRetryableReasonCode,
                        e.getMessage()
                );

                publisher.publish(buildEvent(
                        payload, attempt, IS_DLQ,
                        startedAt, finishedAt,
                        RequestStatus.FAILED_FINAL,
                        ResultCode.FAILED_WORKER_EXCEPTION, // dlq 전략수정
                        new ParticipationProcessedFailure(FailureClass.NON_RETRYABLE, nonRetryableReasonCode, e.getMessage()),
                        false
                ));

                if (nonRetryableDlqCounter != null) nonRetryableDlqCounter.increment();
                meterRegistry.counter("non_retryable_dlq_count", "reasonCode", nonRetryableReasonCode)
                        .increment();

                log.error("[DLQ SEND] reasonCode={} requestId={} eventId={} userId={} attempt={} messageId={}",
                        nonRetryableReasonCode, payload.requestId(), payload.eventId(), payload.userId(), attempt, message.messageId());

                // dlq전략수정 : NonRetryable은 즉시 DLQ 전송 + ACK (send 실패 시 delete까지 가지 않아서 재시도됨)
                dlqSender.send(
                        message.body(),
                        message.messageId(),
                        attempt,
                        "NON_RETRYABLE",
                        nonRetryableReasonCode,
                        Map.of(
                                "requestId", payload.requestId(),
                                "eventId", payload.eventId(),
                                "userId", payload.userId()
                        ),
                        e // dlq전략수정: 예외 전달( digest 채우기 )
                );

                deleteMessage(message);
                return;
            }

            if (retryableExceptionCounter != null) retryableExceptionCounter.increment(); //dlq 전략수정

            log.warn("[RETRYABLE] ex={} attempt={} requestId={} eventId={} userId={} messageId={}",
                    e.getClass().getSimpleName(), attempt, payload.requestId(), payload.eventId(), payload.userId(), message.messageId(), e);

            // dlq전략수정 : Retryable은 "throw/재시도"가 목표.
            // - 폴링 방식이므로 deleteMessage만 안 하면 SQS가 재전달함
            // - 하지만 현재 상태가 PROCESSING에 머물면 다음 시도에서 재처리가 막히므로 QUEUED로 롤백이 필요
            boolean rolledBack = false;
            try {
                // dlq전략수정 : PROCESSING -> QUEUED 롤백 (RequestStateRepository에 메서드 추가 필요: releaseProcessingToQueued)
                rolledBack = requestStateRepository.releaseProcessingToQueued(payload.requestId(), startedAt); //dlq 전략수정
            } catch (Exception rollbackEx) {
                // 롤백 실패는 더 큰 문제(상태 오염 가능). 그래도 ACK는 하지 않는다.
                log.warn("[ROLLBACK FAIL] requestId={} attempt={} (processing->queued)", payload.requestId(), attempt, rollbackEx);
            }

            // [수정] RETRYABLE 무한 재등장 방지
            if (attempt <= MAX_RETRYABLE_RETRY) {
                int backoff = computeRetryableBackoffSeconds(attempt);
                log.warn("[RETRYABLE/DEFER] requestId={} attempt={} backoff={}s rollbackToQueued={}", //dlq전략수정 (warn로 낮춤)
                        payload.requestId(), attempt, backoff, rolledBack, e);
                deferMessage(message, backoff);
                return; // ✅ ACK 금지 (deleteMessage 호출하면 안 됨)
            }

            // dlq전략수정 : 내부에서 RETRYABLE_EXHAUSTED로 FAILED_FINAL 찍고 ACK하면 SQS redrive가 죽음.
            // - attempt가 커져도 계속 "재시도"로 두고, 최종 DLQ 이동은 SQS redrive(maxReceiveCount)에 맡긴다.
            int backoff = computeRetryableBackoffSeconds(attempt); // 이미 max 60초로 캡됨
            log.warn("[RETRYABLE] requestId={} attempt={} backoff={}s rollbackToQueued={} (no-ack; rely on redrive)", //dlq전략수정 (warn로 낮춤)
                    payload.requestId(), attempt, backoff, rolledBack, e);
            deferMessage(message, backoff);
            return; // ✅ ACK 금지 (deleteMessage 호출하면 안 됨)
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

        // 1) 명백한 NonRetryable: 입력/스키마/파싱/비즈니스 규칙
        if (e instanceof IllegalArgumentException) return FailureClass.NON_RETRYABLE; // 필수값 누락 등
        if (e instanceof BusinessRuleViolationException) return FailureClass.NON_RETRYABLE; //dlq 전략수정 : 비즈니스 규칙 위반은 즉시 DLQ

        //dlq 전략수정 : 네트워크/타임아웃은 Retryable로 “명시”
        if (e instanceof SocketTimeoutException) return FailureClass.RETRYABLE;
        if (e instanceof TimeoutException) return FailureClass.RETRYABLE;
        if (e instanceof ConnectException) return FailureClass.RETRYABLE;
        if (e instanceof UnknownHostException) return FailureClass.RETRYABLE;
        if (e instanceof IOException) return FailureClass.RETRYABLE;

        //dlq 전략수정 : AWS SDK(core) 예외는 의존성/버전에 따라 클래스가 없을 수 있으므로 이름으로 안전 판별
        String cn = e.getClass().getName();
        if (cn.endsWith("SdkClientException") || cn.contains(".core.exception.SdkClientException")) {
            return FailureClass.RETRYABLE;
        }

        // 2) DynamoDB: throttling/일시 장애는 Retryable로 명확히 분기
        // - DynamoDbException은 statusCode만으로 판단하면 Throttling(429 등)이 NonRetryable로 오판될 수 있음
        // - SDK 버전에 따라 ThrottlingException/ServiceUnavailableException 클래스가 없을 수 있으므로 errorCode/statusCode로 처리한다.
        if (e instanceof software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException)
            return FailureClass.RETRYABLE;

        // 4) SQS / DynamoDB 공통: 5xx는 Retryable, 4xx는 NonRetryable (단 throttling은 위에서 이미 처리)
        if (e instanceof SqsException se) {
            int sc = se.statusCode();
            if (sc >= 500) return FailureClass.RETRYABLE;
            if (sc >= 400) return FailureClass.NON_RETRYABLE;
        }

        if (e instanceof DynamoDbException de) {
            int sc = de.statusCode();

            //dlq 전략수정 : throttling/limit 계열은 4xx여도 Retryable로 우선 처리
            String code = de.awsErrorDetails() != null ? de.awsErrorDetails().errorCode() : null;
            if (sc == 429) return FailureClass.RETRYABLE;
            if (code != null) {
                if ("ThrottlingException".equals(code) ||
                        "Throttling".equals(code) ||
                        "RequestLimitExceeded".equals(code) ||
                        "ProvisionedThroughputExceededException".equals(code)) {
                    return FailureClass.RETRYABLE;
                }
            }

            if (sc >= 500) return FailureClass.RETRYABLE;
            if (sc >= 400) return FailureClass.NON_RETRYABLE;
        }

        // 5) 그 외는 기본 Retryable(일시 장애 가정)로 두는 게 안전
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
