package com.teamA.async.worker.analytics.event;

//운영/추적에 유용한 부가정보
public record ParticipationProcessedMeta(
        String workerId
) {
    public ParticipationProcessedMeta {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
    }
}
