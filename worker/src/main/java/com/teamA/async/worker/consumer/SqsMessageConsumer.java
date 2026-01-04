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
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Optional;

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

        for (Message message : sqsClient.receiveMessage(request).messages()) {
            handleMessage(message);
        }
    }

    private void handleMessage(Message message) {
        ParticipationMessage payload;

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

        boolean acquired = requestStateRepository.tryAcquireProcessing(payload.requestId());

        if (!acquired) {
            Optional<String> status = requestStateRepository.getCurrentStatus(payload.requestId());
            if (status.isEmpty() || !"PROCESSING".equals(status.get())) {
                deleteMessage(message);
            }
            return;
        }

        try {
            if ("FIRST_COME".equals(payload.eventType())) {
                boolean gotSlot = eventCapacityRepository.tryDecrement(payload.eventId());

                if (gotSlot) {
                    requestStateRepository.markSucceeded(payload.requestId());
                } else {
                    requestStateRepository.markRejectedCapacity(payload.requestId());
                }

                deleteMessage(message);
                return;
            }

            requestStateRepository.markSucceeded(payload.requestId());
            deleteMessage(message);

        } catch (Exception e) {
            // retry → DLQ
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
