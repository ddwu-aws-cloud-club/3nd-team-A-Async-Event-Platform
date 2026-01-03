package com.teamA.async.common.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teamA.async.common.domain.enums.EventType;

public record ParticipationMessage(
        String requestId,
        String eventId,
        EventType eventType
) {
    @JsonCreator
    public ParticipationMessage(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") EventType eventType
    ) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (eventType == null) throw new IllegalArgumentException("eventType is required");
        this.requestId = requestId;
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
