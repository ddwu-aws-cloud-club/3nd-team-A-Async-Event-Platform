package com.teamA.async.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.messaging.ParticipationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipationService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    public String participate(String eventId, String userId, EventType eventType) {

        String requestId = newRequestId();
        long now = System.currentTimeMillis();

        try {
            ParticipationMessage msg =
                    new ParticipationMessage(requestId, eventId, userId, now, eventType);

            String body = objectMapper.writeValueAsString(msg);

            sqsClient.sendMessage(r -> r
                    .queueUrl(queueUrl)
                    .messageBody(body)
            );

            return requestId;

        } catch (Exception e) {
            log.error("SQS enqueue failed", e);
            throw new RuntimeException("enqueue failed");
        }
    }

    private String newRequestId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
