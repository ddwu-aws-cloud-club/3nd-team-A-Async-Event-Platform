package com.teamA.async.admin.api.dto;

import com.teamA.async.admin.domain.EventStatus;
import com.teamA.async.admin.domain.EventType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AdminEventDtos {

    @Getter @Setter @NoArgsConstructor
    public static class CreateEventRequest {
        private String title;
        private EventType type;
        private Long capacityTotal; // FIRST_COME only
        private Long openAt;        // epoch millis (optional)
        private Long closeAt;       // epoch millis (optional)
    }

    @Getter
    public static class CreateEventResponse {
        private final String eventId;
        private final EventStatus status;

        public CreateEventResponse(String eventId, EventStatus status) {
            this.eventId = eventId;
            this.status = status;
        }
    }

    @Getter
    public static class EventStatusResponse {
        private final String eventId;
        private final EventStatus status;

        public EventStatusResponse(String eventId, EventStatus status) {
            this.eventId = eventId;
            this.status = status;
        }
    }

    @Getter
    public static class GetEventResponse {
        private final String eventId;
        private final String title;
        private final EventType type;
        private final EventStatus status;
        private final Long capacityTotal;
        private final Long openAt;
        private final Long closeAt;
        private final Long createdAt;
        private final Long updatedAt;

        public GetEventResponse(
                String eventId, String title, EventType type, EventStatus status,
                Long capacityTotal, Long openAt, Long closeAt,
                Long createdAt, Long updatedAt
        ) {
            this.eventId = eventId;
            this.title = title;
            this.type = type;
            this.status = status;
            this.capacityTotal = capacityTotal;
            this.openAt = openAt;
            this.closeAt = closeAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
