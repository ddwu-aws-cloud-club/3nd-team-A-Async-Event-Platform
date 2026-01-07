package com.teamA.async.worker.analytics.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.worker.analytics.event.ParticipationProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipationEventBridgePublisher {

    private final EventBridgeClient eventBridgeClient;
    private final ObjectMapper objectMapper;

    // 고정 규칙
    private static final String SOURCE = "kr.ac.dongduk.worker";
    private static final String DETAIL_TYPE = "ParticipationProcessed";

    public void publish(ParticipationProcessedEvent payload) {
        try {
            String detailJson = objectMapper.writeValueAsString(payload);

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .source(SOURCE)
                    .detailType(DETAIL_TYPE)
                    .detail(detailJson)
                    .build();

            PutEventsResponse resp = eventBridgeClient.putEvents(
                    PutEventsRequest.builder().entries(entry).build()
            );

            int failed = resp.failedEntryCount() == null ? 0 : resp.failedEntryCount();

            // 운영 최소 로그 1줄: 실패 카운트
            if (failed == 0) {
                log.info("[EVENTBRIDGE] putEvents ok. requestId={} finalStatus={} resultCode={}",
                        payload.requestId(), payload.finalStatus(), payload.resultCode());
            } else {
                // 실패 시 entry error까지 같이
                log.warn("[EVENTBRIDGE] putEvents failedEntryCount={} requestId={} errors={}",
                        failed,
                        payload.requestId(),
                        resp.entries());
            }

        } catch (Exception e) {
            // 예외 전파 금지(Worker 흐름 끊기면 안 됨)
            log.warn("[EVENTBRIDGE] putEvents exception ignored. requestId={}", payload.requestId(), e);
        }
    }
}
