package com.teamA.async.admin.service;

import com.teamA.async.admin.ddb.CapacityItemRepository;
import com.teamA.async.admin.ddb.EventRepository;
import com.teamA.async.admin.domain.EventItem;
import com.teamA.async.admin.domain.EventStatus;
import com.teamA.async.admin.domain.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final CapacityItemRepository capacityItemRepository;

    // ✅ 추가: 추첨 서비스 주입
    private final LotteryDrawService lotteryDrawService;

    public String createDraft(String title, EventType type, Long capacityTotal, Long openAt, Long closeAt) {
        long now = System.currentTimeMillis();
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 8);

        if (type == null) throw new IllegalArgumentException("type is required");

        if (type == EventType.FIRST_COME) {
            if (capacityTotal == null || capacityTotal <= 0) {
                throw new IllegalArgumentException("FIRST_COME requires capacityTotal > 0");
            }
        } else { // LOTTERY
            if (capacityTotal != null) {
                throw new IllegalArgumentException("LOTTERY must not include capacityTotal");
            }
        }

        eventRepository.putDraft(EventItem.builder()
                .eventId(eventId)
                .title(title)
                .type(type)
                .status(EventStatus.DRAFT)
                .capacityTotal(type == EventType.FIRST_COME ? capacityTotal : null)
                .openAt(openAt)
                .closeAt(closeAt)
                .createdAt(now)
                .updatedAt(now)
                .build());

        if (type == EventType.FIRST_COME) {
            capacityItemRepository.putInitialCapacityForFirstCome(eventId, capacityTotal, now);
        }

        return eventId;
    }

    public EventItem getOrThrow(String eventId) {
        return eventRepository.get(eventId)
                .orElseThrow(() -> new NotFoundException("event not found: " + eventId));
    }

    public EventStatus open(String eventId) {
        EventItem cur = getOrThrow(eventId);
        long now = System.currentTimeMillis();

        if (cur.getStatus() == EventStatus.OPEN) return EventStatus.OPEN;
        if (cur.getStatus() == EventStatus.CLOSED) throw new ConflictException("cannot open CLOSED event");

        try {
            eventRepository.updateStatus(eventId, EventStatus.DRAFT, EventStatus.OPEN, now, now, null);
            return EventStatus.OPEN;
        } catch (ConditionalCheckFailedException e) {
            EventItem after = getOrThrow(eventId);
            if (after.getStatus() == EventStatus.OPEN) return EventStatus.OPEN;
            throw new ConflictException("invalid transition to OPEN");
        }
    }

    public EventStatus close(String eventId) {
        EventItem cur = getOrThrow(eventId);
        long now = System.currentTimeMillis();

        if (cur.getStatus() == EventStatus.CLOSED) return EventStatus.CLOSED;
        if (cur.getStatus() == EventStatus.DRAFT) throw new ConflictException("cannot close DRAFT event");

        try {
            eventRepository.updateStatus(eventId, EventStatus.OPEN, EventStatus.CLOSED, now, null, now);
            return EventStatus.CLOSED;
        } catch (ConditionalCheckFailedException e) {
            EventItem after = getOrThrow(eventId);
            if (after.getStatus() == EventStatus.CLOSED) return EventStatus.CLOSED;
            throw new ConflictException("invalid transition to CLOSED");
        }
    }

    /**
     * ✅ LOTTERY draw
     * - type=LOTTERY
     * - status=CLOSED
     * - lotteryDrawnAt == null
     * - draw 수행 후 event에 lotteryDrawnAt 마킹
     */
    public void drawLottery(String eventId, int winners) {
        long now = System.currentTimeMillis();

        EventItem event = getOrThrow(eventId);

        if (event.getType() != EventType.LOTTERY) {
            throw new ConflictException("not a lottery event");
        }
        if (event.getStatus() != EventStatus.CLOSED) {
            throw new ConflictException("event must be CLOSED");
        }
        if (event.getLotteryDrawnAt() != null) {
            throw new ConflictException("lottery already drawn");
        }
        if (winners <= 0) {
            throw new IllegalArgumentException("winners must be > 0");
        }

        // ✅ 추첨 실행
        lotteryDrawService.draw(eventId, winners);

        // ✅ draw 완료 마킹 (마지막!)
        eventRepository.markLotteryDrawn(eventId, now);
    }


    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
