package com.teamA.async.ingest.api;

import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.ingest.service.ParticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ParticipationController {

    private final ParticipationService participationService;
    @PostMapping("api/events/{eventId}/apply")
    public ResponseEntity<?> participate(
            @PathVariable String eventId,
            @RequestParam EventType eventType,
            @RequestHeader(value="X-User-Id", required=false) String userId
    ) {
        if (userId == null) userId = "anonymous";

        long startNs = System.nanoTime();
        String requestId = null;

        try {
            requestId = participationService.participate(eventId, userId, eventType);

            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            // "202 능력" 계측 로그 (Metric Filter용)
            log.info("INGEST_RESULT status=202 latency_ms={} requestId={} userId={} eventId={} eventType={} sqs_ok=true",
                    latencyMs, requestId, userId, eventId, eventType);

            return ResponseEntity.accepted()
                    .body(Map.of("requestId", requestId));

        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            // 실패도 같이 찍어야 total 대비 비율이 의미 있음
            log.error("INGEST_RESULT status=500 latency_ms={} requestId={} userId={} eventId={} eventType={} sqs_ok=false err={}",
                    latencyMs, requestId, userId, eventId, eventType, e.getClass().getSimpleName());

            throw e; // 기존대로 에러 전파
        }
    }



}