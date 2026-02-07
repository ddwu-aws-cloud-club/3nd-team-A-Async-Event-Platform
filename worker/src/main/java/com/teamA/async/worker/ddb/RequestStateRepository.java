package com.teamA.async.worker.ddb;

import com.teamA.async.common.domain.enums.FailureClass;
import com.teamA.async.common.domain.enums.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import com.teamA.async.common.domain.enums.EventType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RequestStateRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.tables.request-state}")
    private String tableName;

    private static final String ATTR_REQUEST_ID = "requestId";

    /**
     * QUEUED -> PROCESSING 선점
     * - Condition: status = QUEUED
     * - Update: status = PROCESSING, startedAt = startedAtMillis
     */

    public void createReceived(
            String requestId,
            String eventId,
            String userId,
            EventType eventType,
            long requestedAt
    ) {
        long ttlEpochSeconds =
                (System.currentTimeMillis() / 1000) + (60L * 60 * 24 * 30); // 30일

        Map<String, AttributeValue> item = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId), // ✅ PK-only (requestId)
                "eventId", AttributeValue.fromS(eventId),
                "userId", AttributeValue.fromS(userId),
                "status", AttributeValue.fromS("RECEIVED"),
                "requestedAt", AttributeValue.fromN(Long.toString(requestedAt)),
                "eventType", AttributeValue.fromS(eventType.name()),
                "ttl", AttributeValue.fromN(Long.toString(ttlEpochSeconds))
        );

        PutItemRequest req = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(requestId)")
                .build();

        try {
            dynamoDbClient.putItem(req);
        } catch (ConditionalCheckFailedException e) {
            // 이미 있으면 그냥 통과 (중복 생성 방지)
        }
    }

    public void markQueued(String requestId, long queuedAtMillis) {

        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#status = :received")
                .updateExpression("SET #status = :queued, queuedAt = :t")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":received", AttributeValue.fromS("RECEIVED"),
                        ":queued", AttributeValue.fromS("QUEUED"),
                        ":t", AttributeValue.fromN(Long.toString(queuedAtMillis))
                ))
                .build();

        try {
            dynamoDbClient.updateItem(req);
        } catch (ConditionalCheckFailedException e) {
            // 이미 상태 바뀐 경우 → 무시
        }
    }

    public boolean tryAcquireProcessing(String requestId, long startedAtMillis) {

        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#status = :queued")   // QUEUED → PROCESSING
                .updateExpression("SET #status = :processing, #startedAt = :startedAt")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#startedAt", "startedAt"
                ))
                .expressionAttributeValues(Map.of(
                        ":queued", AttributeValue.fromS("QUEUED"),
                        ":processing", AttributeValue.fromS("PROCESSING"),
                        ":startedAt", AttributeValue.fromN(String.valueOf(startedAtMillis))
                ))
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * PROCESSING -> SUCCEEDED
     */
    public boolean markSucceeded(String requestId, long finishedAtMillis) {
        return updateFinalStatus(
                requestId,
                "SUCCEEDED",
                "SUCCESS",
                ResultCode.SUCCESS,
                null,
                finishedAtMillis,
                null,
                null
        );
    }

    /**
     * PROCESSING -> REJECTED (capacity)
     */
    public boolean markRejectedCapacity(String requestId, long finishedAtMillis) {
        return updateFinalStatus(
                requestId,
                "REJECTED",
                "REJECTED",
                ResultCode.REJECTED_CAPACITY,
                null,
                finishedAtMillis,
                null,
                null
        );
    }

    /**
     * PROCESSING -> FAILED_FINAL
     */
    public boolean markFailedFinal(
            String requestId,
            long finishedAtMillis,
            ResultCode resultCode,
            FailureClass failureClass,
            String errorCode,
            String errorMessage
    ) {
        return updateFinalStatus(
                requestId,
                "FAILED_FINAL",
                "FAILED",
                resultCode,
                failureClass,
                finishedAtMillis,
                errorCode,
                errorMessage
        );
    }

    private boolean updateFinalStatus(
            String requestId,
            String targetStatus,
            String uiResult,
            ResultCode resultCode,
            FailureClass failureClass,
            long finishedAtMillis,
            String errorCode,
            String errorMessage
    ) {
        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        String safeErrorMessage = truncate(errorMessage, 500);

        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();

        names.put("#status", "status");
        names.put("#finishedAt", "finishedAt");
        names.put("#uiResult", "uiResult");
        names.put("#resultCode", "resultCode");

        values.put(":processing", AttributeValue.fromS("PROCESSING"));
        values.put(":target", AttributeValue.fromS(targetStatus));
        values.put(":finishedAt", AttributeValue.fromN(String.valueOf(finishedAtMillis)));
        values.put(":uiResult", AttributeValue.fromS(uiResult));
        values.put(":resultCode", AttributeValue.fromS(resultCode.name()));

        String updateExpr =
                "SET #status = :target, #finishedAt = :finishedAt, #uiResult = :uiResult, #resultCode = :resultCode";

        if (failureClass != null) {
            names.put("#failureClass", "failureClass");
            values.put(":failureClass", AttributeValue.fromS(failureClass.name()));
            updateExpr += ", #failureClass = :failureClass";
        }

        if (errorCode != null && !errorCode.isBlank()) {
            names.put("#errorCode", "errorCode");
            values.put(":errorCode", AttributeValue.fromS(errorCode));
            updateExpr += ", #errorCode = :errorCode";
        }

        if (safeErrorMessage != null && !safeErrorMessage.isBlank()) {
            names.put("#errorMessage", "errorMessage");
            values.put(":errorMessage", AttributeValue.fromS(safeErrorMessage));
            updateExpr += ", #errorMessage = :errorMessage";
        }

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
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

    /**
     * 상태 조회 (선점 실패 시 로깅/분기용)
     *
     * ✅ 수정 포인트:
     * - DynamoDB 일시 장애를 Optional.empty()로 삼키지 않는다
     * - 상위에서 Retryable로 분류되도록 예외를 그대로 던진다
     */
    public Optional<String> getCurrentStatus(String requestId) {

        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        try {
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(tableName)
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

            return Optional.ofNullable(item.get("status"))
                    .map(AttributeValue::s);

        } catch (DynamoDbException e) {
            // ✅ 일시 장애는 상위에서 Retryable로 처리되도록 그대로 던진다
            throw e;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }

    public boolean releaseProcessingToQueued(String requestId) {

        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#status = :processing")
                .updateExpression("SET #status = :queued")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":processing", AttributeValue.fromS("PROCESSING"),
                        ":queued", AttributeValue.fromS("QUEUED")
                ))
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public boolean releaseProcessingToQueued(String requestId, long startedAtMillis) {

        Map<String, AttributeValue> key = Map.of(
                ATTR_REQUEST_ID, AttributeValue.fromS(requestId)
        );

        long now = System.currentTimeMillis();

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#status = :processing AND #startedAt = :startedAt")
                .updateExpression("SET #status = :queued, lastRetryAt = :now")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#startedAt", "startedAt"
                ))
                .expressionAttributeValues(Map.of(
                        ":processing", AttributeValue.fromS("PROCESSING"),
                        ":queued", AttributeValue.fromS("QUEUED"),
                        ":startedAt", AttributeValue.fromN(Long.toString(startedAtMillis)),
                        ":now", AttributeValue.fromN(Long.toString(now))
                ))
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
