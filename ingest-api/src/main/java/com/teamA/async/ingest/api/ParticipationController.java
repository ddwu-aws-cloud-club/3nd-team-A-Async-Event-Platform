package com.teamA.async.ingest.api;

import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.ingest.api.dto.ParticipationResponse;
import com.teamA.async.ingest.auth.UserResolver;
import com.teamA.async.ingest.service.ParticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ParticipationController {

    private final ParticipationService participationService;
    private final UserResolver userResolver;

    @PostMapping("api/events/{eventId}/apply")
    public ResponseEntity<ParticipationResponse> participate(@PathVariable String eventId, @RequestParam EventType eventType) {
        String userId = userResolver.currentUserId(); // JWT에서만 추출

        log.info("[INGEST HIT] eventId={}, userId={}", eventId, userId);

        ParticipationResponse res = participationService.participate(eventId, userId, eventType);
        return ResponseEntity.accepted().body(res); // 항상 202

    }
}