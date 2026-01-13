package com.teamA.async.ingest.api;

import com.teamA.async.ingest.api.dto.ParticipationResponse;
import com.teamA.async.ingest.auth.UserResolver;
import com.teamA.async.ingest.service.ParticipationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ParticipationController {

    private final ParticipationService participationService;
    private final UserResolver userResolver;

    @PostMapping("/events/{eventId}/participations")
    public ResponseEntity<ParticipationResponse> participate(@PathVariable String eventId) {
        String userId = userResolver.currentUserId(); // JWT에서만 추출
        ParticipationResponse res = participationService.participate(eventId, userId);
        return ResponseEntity.accepted().body(res); // 항상 202
    }
}
