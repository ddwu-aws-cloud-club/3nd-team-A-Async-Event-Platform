package com.teamA.async.worker.ddb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.Map;

/**
 * FIRST_COME 이벤트의 정원(capacity)을 원자적으로 차감하기 위한 Repository
 *
 * 역할
 * - CapacityItem 단일 엔티티를 원자적으로 갱신
 * - 멀티 Worker 환경에서 over-booking 방지
 *
 * 전제
 * - PK = EVENT#{eventId}
 * - SK = CAPACITY
 * - capacityRemaining > 0 일 때만 차감
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EventCapacityRepository {

    private final DynamoDbClient dynamoDbClient;

    private static final String TABLE_NAME = "AsyncEventTable";

    /**
     * capacityRemaining > 0 인 경우에만 1 감소
     *
     * @param eventId 이벤트 ID
     * @return
     *  true  : 차감 성공 (슬롯 확보)
     *  false : 정원 초과 (정책적 실패)
     */
    public boolean tryDecrement(String eventId) {

        // CapacityItem 고정 키
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s("EVENT#" + eventId).build(),
                "SK", AttributeValue.builder().s("CAPACITY").build()
        );

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)

                // 정원이 남아 있을 때만 통과
                .conditionExpression("capacityRemaining > :zero")

                // 원자적 감소 + 갱신 시각 기록
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

            log.info(
                    "[CAPACITY UPDATED] eventId={} capacityRemaining--",
                    eventId
            );
            return true;

        } catch (ConditionalCheckFailedException e) {
            // capacityRemaining <= 0
            log.info(
                    "[CAPACITY FULL] eventId={} no remaining slot",
                    eventId
            );
            return false;
        }
    }
}
