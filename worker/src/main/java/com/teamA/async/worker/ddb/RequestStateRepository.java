package com.teamA.async.worker.ddb;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RequestStateRepository {

    private final DynamoDbClient dynamoDbClient;

    private static final String TABLE_NAME = "AsyncEventTable";

    /**
     * QUEUED → PROCESSING 선점 시도
     */
    public boolean tryAcquireProcessing(String requestId) {
        Map<String, AttributeValue> key = key(requestId);

        Map<String, String> names = Map.of(
                "#status", "status",
                "#startedAt", "startedAt"
        );

        Map<String, AttributeValue> values = Map.of(
                ":queued", AttributeValue.builder().s("QUEUED").build(),
                ":processing", AttributeValue.builder().s("PROCESSING").build(),
                ":now", AttributeValue.builder()
                        .n(String.valueOf(Instant.now().toEpochMilli()))
                        .build()
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .conditionExpression("#status = :queued")
                .updateExpression("SET #status = :processing, #startedAt = :now")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * PROCESSING → SUCCEEDED
     * + finishedAt, uiResult, resultCode 세팅
     */
    public boolean markSucceeded(String requestId) {
        return updateFinalStatus(
                requestId,
                "SUCCEEDED",
                "SUCCESS",
                "SUCCESS",
                null
        );
    }

    /**
     * PROCESSING → REJECTED (정원 초과)
     * + finishedAt, uiResult, resultCode, failureClass 세팅
     */
    public boolean markRejectedCapacity(String requestId) {
        return updateFinalStatus(
                requestId,
                "REJECTED",
                "REJECTED",
                "REJECTED_CAPACITY",
                "NON_RETRYABLE"
        );
    }

    /**
     * PROCESSING → FAILED_FINAL (필요 시 사용)
     */
    public boolean markFailedFinal(String requestId, String resultCode) {
        return updateFinalStatus(
                requestId,
                "FAILED_FINAL",
                "FAILED",
                resultCode,
                "RETRYABLE"
        );
    }

    /**
     * 공통 최종 상태 전이 로직
     *
     * - Condition: status == PROCESSING 일 때만 확정
     * - 멱등성 보장: 이미 최종 상태면 ConditionalCheckFailedException → false
     */
    private boolean updateFinalStatus(
            String requestId,
            String targetStatus,
            String uiResult,
            String resultCode,
            String failureClass
    ) {
        Map<String, AttributeValue> key = key(requestId);

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");
        names.put("#finishedAt", "finishedAt");
        names.put("#uiResult", "uiResult");
        names.put("#resultCode", "resultCode");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":processing", AttributeValue.builder().s("PROCESSING").build());
        values.put(":target", AttributeValue.builder().s(targetStatus).build());
        values.put(":now", AttributeValue.builder()
                .n(String.valueOf(Instant.now().toEpochMilli()))
                .build());
        values.put(":uiResult", AttributeValue.builder().s(uiResult).build());
        values.put(":resultCode", AttributeValue.builder().s(resultCode).build());

        String updateExpression =
                "SET #status = :target, #finishedAt = :now, #uiResult = :uiResult, #resultCode = :resultCode";

        if (failureClass != null) {
            names.put("#failureClass", "failureClass");
            values.put(":failureClass", AttributeValue.builder().s(failureClass).build());
            updateExpression += ", #failureClass = :failureClass";
        }

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .conditionExpression("#status = :processing")
                .updateExpression(updateExpression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * 현재 상태 조회
     */
    public Optional<String> getCurrentStatus(String requestId) {
        Map<String, AttributeValue> key = key(requestId);

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .consistentRead(true)
                .projectionExpression("#status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .build();

        Map<String, AttributeValue> item =
                dynamoDbClient.getItem(request).item();

        if (item == null || !item.containsKey("status")) {
            return Optional.empty();
        }

        return Optional.of(item.get("status").s());
    }

    private Map<String, AttributeValue> key(String requestId) {
        return Map.of(
                "PK", AttributeValue.builder().s("REQ#" + requestId).build(),
                "SK", AttributeValue.builder().s("META").build()
        );
    }
}
