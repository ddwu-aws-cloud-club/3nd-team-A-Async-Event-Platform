package com.teamA.async.worker.analytics.event;

//지표 계산에 필요한 시간관련 3가지
public record ParticipationProcessedTimestamps(
        long queuedAt,
        long startedAt,
        long finishedAt
) {
    public ParticipationProcessedTimestamps {
        // Step0 고정: queuedAt은 "존재"해야 함 (생성은 Step3에서 금지 / 계승)
        if (queuedAt <= 0) throw new IllegalArgumentException("queuedAt must be positive");
        if (startedAt <= 0) throw new IllegalArgumentException("startedAt must be positive");
        if (finishedAt <= 0) throw new IllegalArgumentException("finishedAt must be positive");
    }
}
