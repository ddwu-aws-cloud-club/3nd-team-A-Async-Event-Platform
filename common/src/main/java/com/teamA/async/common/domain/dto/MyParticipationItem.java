package com.teamA.async.common.domain.dto;

import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class MyParticipationItem {
    private String requestId;
    private String eventId;
    private RequestStatus status;
    private Long queuedAt;

    public static MyParticipationItem from(RequestItem item) {
        return MyParticipationItem.builder()
                .requestId(item.getRequestId())
                .eventId(item.getEventId())
                .status(item.getStatus())
                .queuedAt(item.getQueuedAt())
                .build();
    }
}
