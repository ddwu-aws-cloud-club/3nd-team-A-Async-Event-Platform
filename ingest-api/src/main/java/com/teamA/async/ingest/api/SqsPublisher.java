package com.teamA.async.ingest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@RequiredArgsConstructor
public class SqsPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    public void publish(String requestId, String userId, Object body) {
        try {
            MessageEnvelope env = new MessageEnvelope(requestId, userId, body);

            String json = objectMapper.writeValueAsString(env);

            sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(json)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException("SQS publish failed", e);
        }
    }

    record MessageEnvelope(
            String requestId,
            String userId,
            Object body
    ) {}
}
