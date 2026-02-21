package com.teamA.async.ingest.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingest")
public class IngestController {

    private final SqsPublisher sqsPublisher;

    @PostMapping("/event")
    public ResponseEntity<?> ingest(@RequestBody IngestRequest req,
                                    @RequestHeader(value="X-User-Id", required=false) String userId) {

        // 최소 검증만
        if (req.getEventType() == null || req.getPayload() == null) {
            return ResponseEntity.badRequest().build();
        }

        String requestId = UUID.randomUUID().toString();

        // 요청 수신 로그 (requestId 생성 직후)
        log.info("[INGEST][RECEIVED] requestId={} eventId={}", requestId, req.getEventType());

        try {
            sqsPublisher.publish(requestId, userId, req);

            // SQS publish 성공 후, 202 반환 직전 로그
            log.info("[INGEST][ACCEPTED_202][ENQUEUED] requestId={} eventId={}", requestId, req.getEventType());

            // 202
            return ResponseEntity.accepted()
                    .body(java.util.Map.of(
                            "requestId", requestId,
                            "status", "ACCEPTED"
                    ));
        } catch (Exception e) {
            // 예외 로그
            log.error("[INGEST][FAILED] requestId={} eventId={} msg={}", requestId, req.getEventType(), e.getMessage(), e);
            throw e; // 기존 동작 유지 (상위 예외 처리/글로벌 핸들러로 위임)
        }
    }

    @Data
    public static class IngestRequest {
        private String eventType;
        private String payload;
    }
}