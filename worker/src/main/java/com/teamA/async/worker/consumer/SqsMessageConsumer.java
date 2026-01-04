package com.teamA.async.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.worker.ddb.EventCapacityRepository;
import com.teamA.async.worker.ddb.RequestStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * SQS 메시지를 소비하여 Request를 처리하는 Worker 진입점
 *
 * 요구사항(1~6) 반영 포인트
 * - 메시지 파싱 실패: NON-RETRYABLE → 즉시 ack(delete)
 * - 상태 선점: QUEUED → PROCESSING 조건부 업데이트로 단일 Worker만 처리
 * - FIRST_COME만 capacity 로직 진입
 * - capacityRemaining > 0 일 때만 원자적 감소
 * - 감소 성공/실패에 따라 PROCESSING → SUCCEEDED / REJECTED_CAPACITY 조건부 전이
 * - 이미 최종 상태(또는 선점 실패)면 capacity 로직 실행 없이 즉시 ack(delete)로 멱등 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final RequestStateRepository requestStateRepository;
    private final EventCapacityRepository eventCapacityRepository;

    /**
     * worker/application.yml
     * sqs:
     *   queue-url: ...
     */
    @Value("${sqs.queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelay = 3000)
    public void pollMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
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

        // 0) 메시지 역직렬화
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

        // 1) QUEUED → PROCESSING 선점
        boolean acquired = requestStateRepository.tryAcquireProcessing(payload.requestId());

        if (!acquired) {
            // 2) 선점 실패 → 멱등 방어
            requestStateRepository.getCurrentStatus(payload.requestId())
                    .ifPresentOrElse(
                            status -> log.info("[SKIP] requestId={} already status={}", payload.requestId(), status),
                            () -> log.info("[SKIP] requestId={} no item found", payload.requestId())
                    );

            deleteMessage(message);
            return;
        }

        // 3) 단일 Worker만 진입
        try {
            // ✅ FIRST_COME 이벤트만 capacity 로직 수행 (enum 비교)
            if (payload.eventType() == EventType.FIRST_COME) {

                boolean gotSlot = eventCapacityRepository.tryDecrement(payload.eventId());

                if (gotSlot) {
                    boolean ok = requestStateRepository.markSucceeded(payload.requestId());
                    log.info("[FINAL] requestId={} -> SUCCEEDED (updated={})", payload.requestId(), ok);
                } else {
                    boolean ok = requestStateRepository.markRejectedCapacity(payload.requestId());
                    log.info("[FINAL] requestId={} -> REJECTED_CAPACITY (updated={})", payload.requestId(), ok);
                }

                deleteMessage(message);
                return;
            }

            // G0 범위: FIRST_COME 외 이벤트는 성공 처리
            boolean ok = requestStateRepository.markSucceeded(payload.requestId());
            log.info("[FINAL] requestId={} -> SUCCEEDED (non-FIRST_COME, updated={})", payload.requestId(), ok);
            deleteMessage(message);

        } catch (Exception e) {
            // 예외 발생 시 delete하지 않음 → 재시도 → DLQ
            log.error("[RETRYABLE] worker exception requestId={}", payload.requestId(), e);
        }
    }

    private void deleteMessage(Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }
}
