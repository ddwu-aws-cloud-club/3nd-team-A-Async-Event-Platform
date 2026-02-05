package com.teamA.async.ingest.service;

import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.ingest.api.dto.ParticipationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipationService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    public ParticipationResponse participate(String eventId, String userId, EventType eventType) {

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

            return new ParticipationResponse(requestId, false);

        } catch (Exception e) {
            log.error("SQS enqueue failed", e);

            // ingest는 실패해도 상태관리 안 함 — 그냥 에러만 로그
            throw new RuntimeException("enqueue failed");
        }
    }

    private String newRequestId() {
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
