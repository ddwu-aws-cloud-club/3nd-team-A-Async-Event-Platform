package com.teamA.async.ingest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
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

            // send 직전 로그
            String eventId = null;
            if (body instanceof IngestController.IngestRequest req) {
                eventId = req.getEventType();
            }
            log.info("[ENQUEUE][TRY] queueUrl={} requestId={} eventId={}",
                    queueUrl, requestId, eventId);

            SendMessageResponse resp = sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(json)
                            .build()
            );

            // send 성공 직후 로그
            log.info("[ENQUEUE][OK] queueUrl={} messageId={} requestId={} eventId={}",
                    queueUrl, resp.messageId(), requestId, eventId);

        } catch (Exception e) {
            // 예외 발생 시 로그
            log.error("[ENQUEUE][FAIL] queueUrl={} requestId={} msg={}",
                    queueUrl, requestId, e.getMessage(), e);
            throw new RuntimeException("SQS publish failed", e);
        }
    }

    record MessageEnvelope(
            String requestId,
            String userId,
            Object body
    ) {}
}