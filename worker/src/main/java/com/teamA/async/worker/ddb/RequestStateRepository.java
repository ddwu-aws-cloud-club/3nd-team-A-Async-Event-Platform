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
     */
    public boolean markSucceeded(String requestId) {
        return updateFinalStatus(
                requestId,
                "SUCCEEDED",
                null
        );
    }

    /**
     * PROCESSING → REJECTED
     */
    public boolean markRejected(String requestId, String resultCode) {
        return updateFinalStatus(
                requestId,
                "REJECTED",
                resultCode
        );
    }

    /**
     * PROCESSING → FAILED_FINAL
     */
    public boolean markFailedFinal(String requestId, String resultCode) {
        return updateFinalStatus(
                requestId,
                "FAILED_FINAL",
                resultCode
        );
    }

    /**
     * 공통 최종 상태 전이 로직
     */
    private boolean updateFinalStatus(
            String requestId,
            String targetStatus,
            String resultCode
    ) {
        Map<String, AttributeValue> key = key(requestId);

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");
        names.put("#finishedAt", "finishedAt");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":processing", AttributeValue.builder().s("PROCESSING").build());
        values.put(":target", AttributeValue.builder().s(targetStatus).build());
        values.put(":now", AttributeValue.builder()
                .n(String.valueOf(Instant.now().toEpochMilli()))
                .build());

        String updateExpression =
                "SET #status = :target, #finishedAt = :now";

        if (resultCode != null) {
            names.put("#resultCode", "resultCode");
            values.put(":resultCode", AttributeValue.builder().s(resultCode).build());
            updateExpression += ", #resultCode = :resultCode";
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
            return true; // ✅ 최종 상태 확정 성공
        } catch (ConditionalCheckFailedException e) {
            return false; // ❌ 이미 다른 상태 (중복 메시지 등)
        }
    }

    /**
     * 현재 상태 조회 (Step 4에서 사용)
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
