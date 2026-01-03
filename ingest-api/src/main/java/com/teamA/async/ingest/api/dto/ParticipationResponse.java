package com.teamA.async.ingest.api.dto;

public record ParticipationResponse(
        String requestId,
        boolean isDuplicate
) {}
