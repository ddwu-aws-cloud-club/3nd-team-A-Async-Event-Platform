package com.teamA.async.ingest.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

        sqsPublisher.publish(requestId, userId, req);

        // 202
        return ResponseEntity.accepted()
                .body(java.util.Map.of(
                        "requestId", requestId,
                        "status", "ACCEPTED"
                ));
    }

    @Data
    public static class IngestRequest {
        private String eventType;
        private String payload;
    }
}
