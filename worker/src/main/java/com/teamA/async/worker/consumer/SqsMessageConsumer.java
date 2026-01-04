package com.teamA.async.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.worker.ddb.EventCapacityRepository;
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

/**
 * SQS 메시지를 소비하여 Request를 처리하는 Worker 진입점
 *
 * 흐름:
 * 1. 메시지 파싱
 * 2. QUEUED → PROCESSING 선점 (멱등성)
 * 3. 이벤트 타입별 처리
 * 4. 최종 상태 확정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RequestStateRepository requestStateRepository;
    private final EventCapacityRepository eventCapacityRepository;

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

        /* 1️⃣ 메시지 역직렬화 */
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
            deleteMessage(message);
            return;
        }

        /* 2️⃣ QUEUED → PROCESSING 선점 */
        boolean acquired =
                requestStateRepository.tryAcquireProcessing(payload.requestId());

        if (!acquired) {
            Optional<String> statusOpt =
                    requestStateRepository.getCurrentStatus(payload.requestId());

            if (statusOpt.isEmpty()) {
                deleteMessage(message);
                return;
            }

            switch (statusOpt.get()) {
                case "RECEIVED", "QUEUED" -> {
                    // 아직 처리 대상 아님
                    return;
                }
                case "PROCESSING", "SUCCEEDED", "REJECTED", "FAILED_FINAL" -> {
                    // 이미 처리됨 → ack
                    deleteMessage(message);
                    return;
                }
                default -> {
                    deleteMessage(message);
                    return;
                }
            }
        }

        log.info(
                "[ACQUIRED] processing started. requestId={}, eventId={}",
                payload.requestId(),
                payload.eventId()
        );

        /* 3️⃣ 단일 Worker만 진입 */
        try {
            if ("FIRST_COME".equals(payload.eventType())) {

                log.info(
                        "[FIRST_COME] capacity check start. requestId={}, eventId={}",
                        payload.requestId(),
                        payload.eventId()
                );

                boolean gotSlot =
                        eventCapacityRepository.tryDecrement(payload.eventId());

                if (!gotSlot) {
                    log.info(
                            "[CAPACITY REJECTED] requestId={}, eventId={}",
                            payload.requestId(),
                            payload.eventId()
                    );

                    requestStateRepository.markRejectedCapacity(payload.requestId());
                    deleteMessage(message);
                    return;
                }

                log.info(
                        "[CAPACITY ACQUIRED] requestId={}, eventId={}",
                        payload.requestId(),
                        payload.eventId()
                );
            }

            /* 4️⃣ 최종 성공 확정 */
            requestStateRepository.markSucceeded(payload.requestId());

            log.info(
                    "[FINAL] SUCCEEDED requestId={}, eventId={}",
                    payload.requestId(),
                    payload.eventId()
            );

            deleteMessage(message);

        } catch (Exception e) {
            log.error(
                    "[RETRYABLE] exception during processing requestId={}",
                    payload.requestId(),
                    e
            );
            // 메시지 삭제하지 않음 → 재시도
        }
    }

    private void deleteMessage(Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }
}
