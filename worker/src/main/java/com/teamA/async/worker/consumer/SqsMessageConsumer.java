package com.teamA.async.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.worker.ddb.RequestStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RequestStateRepository requestStateRepository;

    private static final String QUEUE_URL =
            "https://sqs.ap-northeast-2.amazonaws.com/590807098068/AsyncEventMainQueue";

    @Scheduled(fixedDelay = 3000)
    public void pollMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(5)
                .build();

        List<Message> messages = sqsClient.receiveMessage(request).messages();

        for (Message message : messages) {
            handleMessage(message);
        }
    }

    private void handleMessage(Message message) {
        ParticipationMessage payload;

        /* 1️⃣ 메시지 파싱 + 기본 검증 */
        try {
            payload = objectMapper.readValue(message.body(), ParticipationMessage.class);
            log.info(
                    "[PARSED OK] requestId={}, eventId={}, eventType={}",
                    payload.requestId(),
                    payload.eventId(),
                    payload.eventType()
            );
        } catch (Exception e) {
            log.error("[NON-RETRYABLE] invalid message body={}", message.body(), e);
            // ❗ G0: Non-retryable → FAILED_FINAL 확정 + ack
            // (지금 단계에서는 requestId가 없을 수 있으므로 DDB 전이는 생략)
            deleteMessage(message);
            return;
        }

        /* 2️⃣ QUEUED → PROCESSING 선점 */
        boolean acquired =
                requestStateRepository.tryAcquireProcessing(payload.requestId());

        if (!acquired) {
            // 4️⃣ 선점 실패 분기
            Optional<String> statusOpt =
                    requestStateRepository.getCurrentStatus(payload.requestId());

            if (statusOpt.isEmpty()) {
                log.info("[GHOST] item not found. requestId={}", payload.requestId());
                deleteMessage(message); // ack ✅
                return;
            }

            String status = statusOpt.get();
            switch (status) {
                case "RECEIVED", "QUEUED" -> {
                    log.info("[RETRYABLE] status={}, requestId={}", status, payload.requestId());
                    return; // ack ❌ (재시도)
                }
                case "PROCESSING" -> {
                    log.info("[DUPLICATE] already processing. requestId={}", payload.requestId());
                    deleteMessage(message); // ack ✅
                    return;
                }
                case "SUCCEEDED", "REJECTED", "FAILED_FINAL" -> {
                    log.info("[FINAL] already done. status={}, requestId={}", status, payload.requestId());
                    deleteMessage(message); // ack ✅
                    return;
                }
                default -> {
                    log.warn("[UNKNOWN STATUS] status={}, requestId={}", status, payload.requestId());
                    deleteMessage(message); // 안전하게 ack
                    return;
                }
            }
        }

        log.info("[ACQUIRED] processing started. requestId={}", payload.requestId());

        /* 5️⃣ 최종 상태 전이 (🔥 Step 5 핵심) */
        try {
            // ⚠️ G0에서는 비즈니스 로직 없이 성공 처리로 고정
            boolean ok =
                    requestStateRepository.markSucceeded(payload.requestId());

            log.info(
                    "[FINAL] markSucceeded ok={}, requestId={}",
                    ok,
                    payload.requestId()
            );

            // 최종 상태 확정이든 중복이든 → ack ✅
            deleteMessage(message);

        } catch (Exception e) {
            log.error(
                    "[RETRYABLE] exception during processing. requestId={}",
                    payload.requestId(),
                    e
            );
            // ack ❌ → 재시도 → DLQ
        }
    }

    /* 공통 DeleteMessage 유틸 */
    private void deleteMessage(Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }
}
