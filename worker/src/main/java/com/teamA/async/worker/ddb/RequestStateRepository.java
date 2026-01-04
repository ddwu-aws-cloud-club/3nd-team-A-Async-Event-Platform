package com.teamA.async.worker.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

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
     * QUEUED → PROCESSING 선점
     */
    public boolean tryAcquireProcessing(String requestId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s(DdbKeyFactory.requestPk(requestId))
                        .build(),
                "SK", AttributeValue.builder()
                        .s(DdbKeyFactory.metaSk())
                        .build()
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .conditionExpression("#status = :queued")
                .updateExpression("SET #status = :processing, #startedAt = :now")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#startedAt", "startedAt"
                ))
                .expressionAttributeValues(Map.of(
                        ":queued", AttributeValue.builder().s("QUEUED").build(),
                        ":processing", AttributeValue.builder().s("PROCESSING").build(),
                        ":now", AttributeValue.builder()
                                .n(String.valueOf(Instant.now().toEpochMilli()))
                                .build()
                ))
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public boolean markSucceeded(String requestId) {
        return updateFinalStatus(
                requestId,
                "SUCCEEDED",
                "SUCCESS",
                "SUCCESS",
                null
        );
    }

    public boolean markRejectedCapacity(String requestId) {
        return updateFinalStatus(
                requestId,
                "REJECTED",
                "REJECTED",
                "REJECTED_CAPACITY",
                "NON_RETRYABLE"
        );
    }

    private boolean updateFinalStatus(
            String requestId,
            String targetStatus,
            String uiResult,
            String resultCode,
            String failureClass
    ) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s(DdbKeyFactory.requestPk(requestId))
                        .build(),
                "SK", AttributeValue.builder()
                        .s(DdbKeyFactory.metaSk())
                        .build()
        );

        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();

        names.put("#status", "status");
        names.put("#finishedAt", "finishedAt");
        names.put("#uiResult", "uiResult");
        names.put("#resultCode", "resultCode");

        values.put(":processing", AttributeValue.builder().s("PROCESSING").build());
        values.put(":target", AttributeValue.builder().s(targetStatus).build());
        values.put(":uiResult", AttributeValue.builder().s(uiResult).build());
        values.put(":resultCode", AttributeValue.builder().s(resultCode).build());
        values.put(":now", AttributeValue.builder()
                .n(String.valueOf(Instant.now().toEpochMilli()))
                .build());

        String updateExpr =
                "SET #status = :target, #finishedAt = :now, #uiResult = :uiResult, #resultCode = :resultCode";

        if (failureClass != null) {
            names.put("#failureClass", "failureClass");
            values.put(":failureClass", AttributeValue.builder().s(failureClass).build());
            updateExpr += ", #failureClass = :failureClass";
        }

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .conditionExpression("#status = :processing")
                .updateExpression(updateExpr)
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

    public Optional<String> getCurrentStatus(String requestId) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder()
                        .s(DdbKeyFactory.requestPk(requestId))
                        .build(),
                "SK", AttributeValue.builder()
                        .s(DdbKeyFactory.metaSk())
                        .build()
        );

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .projectionExpression("#status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item =
                dynamoDbClient.getItem(request).item();

        if (item == null || !item.containsKey("status")) {
            return Optional.empty();
        }
        return Optional.of(item.get("status").s());
    }
}
