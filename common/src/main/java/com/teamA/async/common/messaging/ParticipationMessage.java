package com.teamA.async.common.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teamA.async.common.domain.enums.EventType;

public record ParticipationMessage(
        String requestId,
        String eventId,
        String userId,
        long queuedAt,
        EventType eventType
) {
    @JsonCreator
    public ParticipationMessage(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("userId") String userId,
            @JsonProperty("queuedAt") long queuedAt,
            @JsonProperty("eventType") EventType eventType
    ) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        if (queuedAt <= 0) throw new IllegalArgumentException("queuedAt must be positive");
        if (eventType == null) throw new IllegalArgumentException("eventType is required");

        this.requestId = requestId;
        this.eventId = eventId;
        this.userId = userId;
        this.queuedAt = queuedAt;
        this.eventType = eventType;
    }
}
