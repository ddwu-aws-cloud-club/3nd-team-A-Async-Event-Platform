package com.teamA.async.admin.api;

import com.teamA.async.admin.domain.EventItem;
import com.teamA.async.admin.domain.EventStatus;
import com.teamA.async.admin.domain.EventType;
import com.teamA.async.admin.service.AdminEventService;
import com.teamA.async.admin.service.LotteryDrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class LotteryAdminController {

    private final AdminEventService adminEventService;

    @PostMapping("/{eventId}/lottery/draw")
    public ResponseEntity<Void> draw(
            @PathVariable String eventId,
            @RequestParam(name = "winners") int winners
    ) {
        adminEventService.drawLottery(eventId, winners);
        return ResponseEntity.ok().build();
    }
}
