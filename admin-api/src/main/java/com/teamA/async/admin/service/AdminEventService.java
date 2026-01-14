package com.teamA.async.admin.service;

import com.teamA.async.admin.ddb.CapacityItemRepository;
import com.teamA.async.admin.ddb.EventRepository;
import com.teamA.async.admin.domain.EventItem;
import com.teamA.async.admin.domain.EventStatus;
import com.teamA.async.admin.domain.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final EventRepository eventRepository;
    private final CapacityItemRepository capacityItemRepository;

    public String createEvent(String title, EventType type, Long capacityTotal) {
        long now = System.currentTimeMillis();
        String eventId = UUID.randomUUID().toString();

        // LOTTERY면 capacityTotal 금지(또는 무시)
        if (type == EventType.LOTTERY && capacityTotal != null) {
            throw new IllegalArgumentException("LOTTERY event must not have capacityTotal");
        }
        // FIRST_COME면 capacityTotal 필수
        if (type == EventType.FIRST_COME && (capacityTotal == null || capacityTotal <= 0)) {
            throw new IllegalArgumentException("FIRST_COME event requires capacityTotal > 0");
        }

        // 1) Event(DRAFT) 생성
        eventRepository.putDraft(EventItem.builder()
                .eventId(eventId)
                .title(title)
                .type(type)
                .status(EventStatus.DRAFT)
                .capacityTotal(type == EventType.FIRST_COME ? capacityTotal : null)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 2) FIRST_COME이면 CapacityItem 초기화
        if (type == EventType.FIRST_COME) {
            capacityItemRepository.putInitialCapacityForFirstCome(eventId, capacityTotal, now);
        }

        return eventId;
    }
}
