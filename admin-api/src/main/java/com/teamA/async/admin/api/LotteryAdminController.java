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
    private final LotteryDrawService lotteryDrawService;

    @PostMapping("/{eventId}/lottery/draw")
    public ResponseEntity<Void> draw(@PathVariable String eventId) {

        EventItem event = adminEventService.getOrThrow(eventId);

        // 1️⃣ LOTTERY 이벤트만 허용
        if (event.getType() != EventType.LOTTERY) {
            throw new RuntimeException("NOT_LOTTERY_EVENT");
        }

        // 2️⃣ CLOSED 상태에서만 허용
        if (event.getStatus() != EventStatus.CLOSED) {
            throw new RuntimeException("EVENT_NOT_CLOSED");
        }

        // 3️⃣ 재실행 방지
        if (event.getLotteryDrawnAt() != null) {
            throw new RuntimeException("LOTTERY_ALREADY_DRAWN");
        }

        /*
         * G2 이후 합의:
         * - LOTTERY 이벤트는 winners 필드를 추가하지 않는다.
         * - draw 시점에서만 capacityTotal을 "당첨자 수"로 해석한다.
         */
        if (event.getCapacityTotal() == null || event.getCapacityTotal() <= 0) {
            throw new IllegalStateException(
                    "LOTTERY requires capacityTotal as winners at draw time"
            );
        }

        // 4️⃣ 추첨 시점 기록 (1회 보장)
        adminEventService.markLotteryDrawn(eventId);

        // 5️⃣ 추첨 실행
        lotteryDrawService.draw(
                eventId,
                event.getCapacityTotal().intValue()
        );

        return ResponseEntity.ok().build();
    }
}
