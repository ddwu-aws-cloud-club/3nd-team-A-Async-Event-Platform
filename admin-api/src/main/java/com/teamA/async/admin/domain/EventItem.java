package com.teamA.async.admin.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventItem {
    private final String eventId;
    private final String title;
    private final EventType type;
    private final EventStatus status;

    private final Long capacityTotal;   // FIRST_COME only (nullable)
    private final Long openAt;          // epoch millis (nullable)
    private final Long closeAt;         // epoch millis (nullable)

    private final Long createdAt;       // epoch millis
    private final Long updatedAt;       // epoch millis
}
