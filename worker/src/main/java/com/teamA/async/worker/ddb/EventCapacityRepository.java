package com.teamA.async.worker.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventCapacityRepository {

    private final DynamoDbClient dynamoDbClient;

    private static final String TABLE_NAME = "AsyncEventTable";

    /**
     * capacityRemaining > 0 인 경우에만 1 감소
     */
    public boolean tryDecrement(String eventId) {

        // 🔒 키팩토리 단일 진실 사용
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s(DdbKeyFactory.eventPk(eventId))
                        .build(),
                "SK", AttributeValue.builder()
                        .s("CONFIG") // ❗ 키팩토리에 메서드는 없고 상수만 존재
                        .build()
        );

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .conditionExpression("capacityRemaining > :zero")
                .updateExpression(
                        "SET capacityRemaining = capacityRemaining - :one, updatedAt = :now"
                )
                .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":one", AttributeValue.builder().n("1").build(),
                        ":now", AttributeValue.builder()
                                .n(String.valueOf(Instant.now().toEpochMilli()))
                                .build()
                ))
                .build();

        try {
            dynamoDbClient.updateItem(request);
            log.info("[CAPACITY UPDATED] eventId={} capacityRemaining--", eventId);
            return true;

        } catch (ConditionalCheckFailedException e) {
            log.info("[CAPACITY FULL] eventId={} no remaining slot", eventId);
            return false;
        }
    }
}
