package com.teamA.async.common.domain.dto;

import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEventRequestItem {
    private String requestId;
    private String eventId;
    private String userId;
    private EventType eventType;
    private RequestStatus status;
    private UiResult uiResult;
    private ResultCode resultCode;
    private Long queuedAt;

    public static AdminEventRequestItem from(RequestItem item) {
        return AdminEventRequestItem.builder()
                .requestId(item.getRequestId())
                .eventId(item.getEventId())
                .userId(item.getUserId())
                .eventType(item.getEventType())
                .status(item.getStatus())
                .uiResult(item.getUiResult() == null ? UiResult.PENDING : item.getUiResult())
                .resultCode(item.getResultCode())
                .queuedAt(item.getQueuedAt())
                .build();
    }
}
