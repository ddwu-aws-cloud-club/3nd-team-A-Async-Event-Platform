package com.teamA.async.admin.ddb;

import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.model.RequestItem;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class DynamoDbItemMapper {

    public static RequestItem toRequestItem(Map<String, AttributeValue> item) {
        return RequestItem.builder()
                .requestId(item.get("requestId").s())
                .eventId(item.get("eventId").s())
                .userId(item.get("userId").s())
                .status(RequestStatus.valueOf(item.get("status").s()))
                .queuedAt(Long.parseLong(item.get("queuedAt").n()))
                .build();
    }
}
