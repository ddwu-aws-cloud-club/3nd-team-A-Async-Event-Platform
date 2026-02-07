package com.teamA.async.worker.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

//dlq전략수정 : Micrometer 메트릭
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqSender {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.dlq-url}")
    private String dlqUrl;


    private final MeterRegistry meterRegistry;
    private Counter dlqSendFailureCounter;

    // dlq전략수정: workerVersion/workerId 메타를 고정해서 넣기
    private String workerId() {
        return Optional.ofNullable(System.getenv("HOSTNAME")).orElse("worker-local");
    }

    private String workerVersion() {
        // dlq전략수정: ECS task env로 주입 추천 (ex. GIT_SHA, IMAGE_TAG)
        return Optional.ofNullable(System.getenv("WORKER_VERSION")).orElse("unknown");
    }

    // dlq전략수정: SQS 256KB 제한 고려해서 너무 큰 body는 잘라 저장
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(truncated)";
    }

    // dlq전략수정: stackTraceDigest(짧은 해시)
    private String digest(String s) {
        try {
            if (s == null) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            // 앞 12 hex 정도면 충분
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) { // 6 bytes => 12 hex
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "digest_failed";
        }
    }

    //dlq전략수정 : 메트릭 초기화(지연 초기화 방식)
    private Counter dlqSendFailureCounter() {
        if (dlqSendFailureCounter == null) {
            dlqSendFailureCounter = Counter.builder("dlq_send_failure_count")
                    .description("DLQ send failures (will cause retry to prevent loss)")
                    .register(meterRegistry);
        }
        return dlqSendFailureCounter;
    }

    // 기존 호출부를 크게 바꾸지 않기 위해, 에러 정보는 optional로 받는 오버로드 형태 추천
    public void send(
            String originalBody,
            String originalQueueMessageId,
            int receiveCount,
            String failureType,   // NON_RETRYABLE
            String reasonCode,    // INVALID_SCHEMA / STALE_RECEIVED / ...
            Map<String, String> keys
    ) {
        send(originalBody, originalQueueMessageId, receiveCount, failureType, reasonCode, keys, null);
    }

    // dlq전략수정: exception까지 받는 버전(가능하면 NonRetryable catch에서 이걸 쓰면 stackTraceDigest까지 완성됨)
    public void send(
            String originalBody,
            String originalQueueMessageId,
            int receiveCount,
            String failureType,
            String reasonCode,
            Map<String, String> keys,
            Exception exceptionOrNull
    ) {
        try {
            String receivedAt = Instant.now().toString();
            String workerId = workerId();
            String workerVersion = workerVersion();

            Map<String, Object> originalMeta = new HashMap<>();
            originalMeta.put("queueMessageId", originalQueueMessageId);
            originalMeta.put("receiveCount", receiveCount);
            originalMeta.put("receivedAt", receivedAt);
            originalMeta.put("workerId", workerId);
            originalMeta.put("workerVersion", workerVersion);

            Map<String, Object> error = null;
            if (exceptionOrNull != null) {
                String exClass = exceptionOrNull.getClass().getSimpleName();
                String exMsg = exceptionOrNull.getMessage();
                String digestBase = exClass + ":" + exMsg;
                error = new HashMap<>();
                error.put("exceptionClass", exClass);
                error.put("message", truncate(exMsg, 300));
                error.put("stackTraceDigest", digest(digestBase));
            }

            Map<String, Object> envelope = new HashMap<>();
            envelope.put("failureType", failureType);
            envelope.put("reasonCode", reasonCode);
            envelope.put("keys", keys == null ? Map.of() : keys);
            envelope.put("original", originalMeta);
            if (error != null) envelope.put("error", error);

            // dlq전략수정: 원본 payload 그대로 + 메타데이터 추가 원칙
            envelope.put("originalBody", truncate(originalBody, 30_000)); // 넉넉히 잘라 저장

            String body = objectMapper.writeValueAsString(envelope);

            // MessageAttributes는 콘솔/필터링용(짧고 핵심만)
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            attrs.put("failureType", MessageAttributeValue.builder().dataType("String").stringValue(failureType).build());
            attrs.put("reasonCode", MessageAttributeValue.builder().dataType("String").stringValue(reasonCode).build());
            attrs.put("originalQueueMessageId", MessageAttributeValue.builder().dataType("String").stringValue(originalQueueMessageId).build());
            attrs.put("workerVersion", MessageAttributeValue.builder().dataType("String").stringValue(workerVersion).build());
            attrs.put("workerId", MessageAttributeValue.builder().dataType("String").stringValue(workerId).build());
            attrs.put("receivedAt", MessageAttributeValue.builder().dataType("String").stringValue(receivedAt).build());

            if (keys != null) {
                keys.forEach((k, v) -> {
                    if (v != null && !v.isBlank()) {
                        attrs.put(k, MessageAttributeValue.builder().dataType("String").stringValue(v).build());
                    }
                });
            }

            //dlq전략수정 : DLQ 전송 로그(운영이 DLQ만 봐도 1분 내 분류 가능하게)
            log.error("[DLQ SEND] failureType={} reasonCode={} keys={} originalQueueMessageId={} receiveCount={} workerVersion={}",
                    failureType, reasonCode, (keys == null ? Map.of() : keys), originalQueueMessageId, receiveCount, workerVersion);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .messageBody(body)
                    .messageAttributes(attrs)
                    .build());

        } catch (Exception e) {
            //dlq전략수정 : DLQ 전송 실패 메트릭(+ reasonCode 태깅)
            try {
                dlqSendFailureCounter().increment();
                meterRegistry.counter("dlq_send_failure_count", "reasonCode",
                                (reasonCode == null || reasonCode.isBlank()) ? "UNKNOWN" : reasonCode)
                        .increment();
            } catch (Exception ignore) {
                // 메트릭 실패가 본 실패를 가리면 안 됨
            }

            //dlq전략수정 : 실패 로그(키 포함)
            log.error("[DLQ SEND FAILED] failureType={} reasonCode={} keys={} originalQueueMessageId={} receiveCount={}",
                    failureType, reasonCode, (keys == null ? Map.of() : keys), originalQueueMessageId, receiveCount, e);

            // 중요: DLQ 전송 실패는 유실 방지를 위해 반드시 실패로 올려 재시도 타게 함
            throw new RuntimeException("DLQ_SEND_FAILED", e);
        }
    }
}
