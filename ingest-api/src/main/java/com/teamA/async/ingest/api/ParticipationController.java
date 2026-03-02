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
    @PostMapping("/api/events/{eventId}/apply")
    public ResponseEntity<?> participate(
            @PathVariable String eventId,
            @RequestParam EventType eventType,
            @RequestHeader(value="X-User-Id", required=false) String userId
    ) {
        if (userId == null) userId = "anonymous";

        String requestId =
                participationService.participate(eventId, userId, eventType);

        return ResponseEntity.accepted()
                .body(Map.of("requestId", requestId));
    }



}