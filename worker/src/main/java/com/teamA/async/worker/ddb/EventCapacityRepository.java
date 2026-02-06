package com.teamA.async.worker.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.worker.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

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

            //dlq전략수정 : "정원 마감" vs "이벤트 없음" 의미 분리
            // - 조건 실패가 "0 이하"일 수도, 아이템이 없어서일 수도 있음
            // - DLQ reasonCode를 의미있게 채우려면 여기서 구분해야 함
            if (!existsEventConfigItem(eventId, key)) {
                log.warn("[EVENT NOT FOUND] eventId={} (CONFIG item missing)", eventId);
                throw new BusinessRuleViolationException(
                        "EVENT_NOT_FOUND",
                        "event config not found: " + eventId
                );
            }

            log.info("[CAPACITY FULL] eventId={} no remaining slot", eventId);
            return false;
        }
    }

    //dlq전략수정 : 이벤트(CONFIG) 아이템 존재 여부 확인용
    private boolean existsEventConfigItem(String eventId, Map<String, AttributeValue> key) {
        try {
            GetItemRequest getReq = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .projectionExpression("PK")
                    .consistentRead(true)
                    .build();

            Map<String, AttributeValue> item = dynamoDbClient.getItem(getReq).item();
            return item != null && !item.isEmpty();
        } catch (Exception ex) {
            // 존재 여부 확인 실패는 "일시 장애" 성격이 더 강하므로 여기서는 존재한다고 가정하지 말고
            // 상위에서 Retryable로 처리되도록 예외를 그대로 올려도 되지만,
            // 여기서는 tryDecrement의 시그니처를 유지하려고 "예외 재던지기"로 간다.
            throw ex;
        }
    }
}
