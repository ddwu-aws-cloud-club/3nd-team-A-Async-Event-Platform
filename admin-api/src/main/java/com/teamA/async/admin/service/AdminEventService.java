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

        // 1) Event 생성(DRAFT)
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

        // 2) FIRST_COME면 CapacityItem 초기화(Worker 스펙: SK=CONFIG)
        if (type == EventType.FIRST_COME) {
            capacityItemRepository.putInitialCapacityForFirstCome(eventId, capacityTotal, now);
        }

        return eventId;
    }

    public EventItem getOrThrow(String eventId) {
        return eventRepository.get(eventId)
                .orElseThrow(() -> new NotFoundException("event not found: " + eventId));
    }

    // 멱등 정책:
    // - 이미 OPEN이면 200
    // - DRAFT면 OPEN으로 전이 200
    // - CLOSED면 409
    public EventStatus open(String eventId) {
        EventItem cur = getOrThrow(eventId);
        long now = System.currentTimeMillis();

        if (cur.getStatus() == EventStatus.OPEN) return EventStatus.OPEN;
        if (cur.getStatus() == EventStatus.CLOSED) throw new ConflictException("cannot open CLOSED event");

        // DRAFT -> OPEN
        try {
            eventRepository.updateStatus(eventId, EventStatus.DRAFT, EventStatus.OPEN, now, now, null);
            return EventStatus.OPEN;
        } catch (ConditionalCheckFailedException e) {
            // 레이스 상황: 누가 먼저 열었을 수도 있음 -> 다시 조회해서 멱등 처리
            EventItem after = getOrThrow(eventId);
            if (after.getStatus() == EventStatus.OPEN) return EventStatus.OPEN;
            throw new ConflictException("invalid transition to OPEN");
        }
    }

    // 멱등 정책:
    // - 이미 CLOSED이면 200
    // - OPEN이면 CLOSED로 전이 200
    // - DRAFT면 409
    public EventStatus close(String eventId) {
        EventItem cur = getOrThrow(eventId);
        long now = System.currentTimeMillis();

        if (cur.getStatus() == EventStatus.CLOSED) return EventStatus.CLOSED;
        if (cur.getStatus() == EventStatus.DRAFT) throw new ConflictException("cannot close DRAFT event");

        // OPEN -> CLOSED
        try {
            eventRepository.updateStatus(eventId, EventStatus.OPEN, EventStatus.CLOSED, now, null, now);
            return EventStatus.CLOSED;
        } catch (ConditionalCheckFailedException e) {
            EventItem after = getOrThrow(eventId);
            if (after.getStatus() == EventStatus.CLOSED) return EventStatus.CLOSED;
            throw new ConflictException("invalid transition to CLOSED");
        }
    }

    // ---- Exceptions (간단 구현) ----
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    public void markLotteryDrawn(String eventId) {
        eventRepository.markLotteryDrawn(eventId, System.currentTimeMillis());
    }


}
