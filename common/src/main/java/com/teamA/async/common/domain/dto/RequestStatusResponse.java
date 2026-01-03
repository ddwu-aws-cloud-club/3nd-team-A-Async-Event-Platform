package com.teamA.async.common.domain.dto;

import com.teamA.async.common.domain.enums.*;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class RequestStatusResponse {
    private String requestId;
    private String eventId;
    private EventType eventType;
    private RequestStatus status;
    private UiResult uiResult;
    private ResultCode resultCode;
    private Timestamps timestamps;

    @Getter @Builder
    public static class Timestamps {
        private Long requestedAt;
        private Long queuedAt;
        private Long startedAt;
        private Long finishedAt;
    }
}
