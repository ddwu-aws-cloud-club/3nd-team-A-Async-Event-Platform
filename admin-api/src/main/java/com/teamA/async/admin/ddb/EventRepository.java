package com.teamA.async.admin.ddb;

import com.teamA.async.admin.ddb.keys.AdminDdbKeyFactory;
import com.teamA.async.admin.domain.EventItem;
import com.teamA.async.admin.domain.EventStatus;
import com.teamA.async.admin.domain.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EventRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    public void putDraft(EventItem item) {
        if (item.getEventId() == null || item.getType() == null || item.getStatus() == null) {
            throw new IllegalStateException("eventId/type/status must not be null");
        }

        Map<String, AttributeValue> map = new HashMap<>();
        map.put("PK", AttributeValue.builder().s(AdminDdbKeyFactory.eventPk(item.getEventId())).build());
        map.put("SK", AttributeValue.builder().s(AdminDdbKeyFactory.metaSk()).build());

        map.put("eventId", AttributeValue.builder().s(item.getEventId()).build());
        if (item.getTitle() != null) {
            map.put("title", AttributeValue.builder().s(item.getTitle()).build());
        }

        map.put("type", AttributeValue.builder().s(item.getType().name()).build());
        map.put("status", AttributeValue.builder().s(item.getStatus().name()).build());

        if (item.getCapacityTotal() != null) {
            map.put("capacityTotal", AttributeValue.builder().n(Long.toString(item.getCapacityTotal())).build());
        }
        if (item.getOpenAt() != null) {
            map.put("openAt", AttributeValue.builder().n(Long.toString(item.getOpenAt())).build());
        }
        if (item.getCloseAt() != null) {
            map.put("closeAt", AttributeValue.builder().n(Long.toString(item.getCloseAt())).build());
        }

        map.put("createdAt", AttributeValue.builder().n(Long.toString(item.getCreatedAt())).build());
        map.put("updatedAt", AttributeValue.builder().n(Long.toString(item.getUpdatedAt())).build());

        // 중복 생성 방지 (eventId 고유)
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(map)
                .conditionExpression("attribute_not_exists(PK)")
                .build());
    }

    public Optional<EventItem> get(String eventId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s(AdminDdbKeyFactory.eventPk(eventId)).build(),
                "SK", AttributeValue.builder().s(AdminDdbKeyFactory.metaSk()).build()
        );

        GetItemResponse res = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build());

        if (!res.hasItem() || res.item().isEmpty()) return Optional.empty();
        return Optional.of(fromItem(res.item()));
    }

    public void updateStatus(String eventId, EventStatus from, EventStatus to, long nowEpochMs, Long openAtEpochMs, Long closeAtEpochMs) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s(AdminDdbKeyFactory.eventPk(eventId)).build(),
                "SK", AttributeValue.builder().s(AdminDdbKeyFactory.metaSk()).build()
        );

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");
        names.put("#updatedAt", "updatedAt");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":from", AttributeValue.builder().s(from.name()).build());
        values.put(":to", AttributeValue.builder().s(to.name()).build());
        values.put(":now", AttributeValue.builder().n(Long.toString(nowEpochMs)).build());

        StringBuilder update = new StringBuilder("SET #status = :to, #updatedAt = :now");

        if (openAtEpochMs != null) {
            names.put("#openAt", "openAt");
            values.put(":openAt", AttributeValue.builder().n(Long.toString(openAtEpochMs)).build());
            update.append(", #openAt = :openAt");
        }

        if (closeAtEpochMs != null) {
            names.put("#closeAt", "closeAt");
            values.put(":closeAt", AttributeValue.builder().n(Long.toString(closeAtEpochMs)).build());
            update.append(", #closeAt = :closeAt");
        }

        // 상태 전이 강제: 현재 상태가 from일 때만 변경
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(update.toString())
                .conditionExpression("#status = :from")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build());
    }

    private EventItem fromItem(Map<String, AttributeValue> item) {
        Long capacityTotal = item.containsKey("capacityTotal") ? Long.parseLong(item.get("capacityTotal").n()) : null;
        Long openAt = item.containsKey("openAt") ? Long.parseLong(item.get("openAt").n()) : null;
        Long closeAt = item.containsKey("closeAt") ? Long.parseLong(item.get("closeAt").n()) : null;

        return EventItem.builder()
                .eventId(item.get("eventId").s())
                .title(item.containsKey("title") ? item.get("title").s() : null)
                .type(EventType.valueOf(item.get("type").s()))
                .status(EventStatus.valueOf(item.get("status").s()))
                .capacityTotal(capacityTotal)
                .openAt(openAt)
                .closeAt(closeAt)
                .createdAt(Long.parseLong(item.get("createdAt").n()))
                .updatedAt(Long.parseLong(item.get("updatedAt").n()))
                .build();
    }
    //Lottery
    public void markLotteryDrawn(String eventId, long drawnAt) {

        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s(AdminDdbKeyFactory.eventPk(eventId))
                        .build(),
                "SK", AttributeValue.builder()
                        .s(AdminDdbKeyFactory.metaSk())
                        .build()
        );

        Map<String, String> names = Map.of(
                "#lotteryDrawnAt", "lotteryDrawnAt",
                "#updatedAt", "updatedAt"
        );

        Map<String, AttributeValue> values = Map.of(
                ":drawnAt", AttributeValue.builder()
                        .n(Long.toString(drawnAt))
                        .build(),
                ":now", AttributeValue.builder()
                        .n(Long.toString(drawnAt))
                        .build()
        );

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #lotteryDrawnAt = :drawnAt, #updatedAt = :now")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build());
    }



}
