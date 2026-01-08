package com.teamA.async.worker.analytics.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.worker.analytics.event.ParticipationProcessedEvent;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meterRegistry; // 메트릭스

    // 고정 규칙
    private static final String SOURCE = "kr.ac.dongduk.worker";
    private static final String DETAIL_TYPE = "ParticipationProcessed";

    public void publish(ParticipationProcessedEvent payload) {

        try {
            // 고의적으로 에러 만듬 -> EventBridge 고장냄
            // if (true) throw new RuntimeException("Test Exception for Step 4");

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
                meterRegistry.counter("worker.eventbridge.exception").increment();
            }

        } catch (Exception e) {
            // 예외 전파 금지(Worker 흐름 끊기면 안 됨)
            log.warn("[EVENTBRIDGE] putEvents exception ignored. requestId={}", payload.requestId(), e);
            meterRegistry.counter("worker.eventbridge.exception").increment();
        }
    }
}
