package com.teamA.async.worker.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.enums.FailureClass;
import com.teamA.async.common.domain.enums.ResultCode;
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
public class RequestStateRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    private static final String ATTR_PK = "PK";
    private static final String ATTR_SK = "SK";

    /**
     * QUEUED -> PROCESSING 선점
     * - Condition: status = QUEUED
     * - Update: status = PROCESSING, startedAt = startedAtMillis
     */
    public boolean tryAcquireProcessing(String requestId, long startedAtMillis) {
        Map<String, AttributeValue> key = Map.of(
                ATTR_PK, AttributeValue.builder().s(DdbKeyFactory.requestPk(requestId)).build(),
                ATTR_SK, AttributeValue.builder().s(DdbKeyFactory.metaSk()).build()
        );

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#status = :queued")
                .updateExpression("SET #status = :processing, #startedAt = :startedAt")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#startedAt", "startedAt"
                ))
                .expressionAttributeValues(Map.of(
                        ":queued", AttributeValue.builder().s("QUEUED").build(),
                        ":processing", AttributeValue.builder().s("PROCESSING").build(),
                        ":startedAt", AttributeValue.builder().n(String.valueOf(startedAtMillis)).build()
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
                null, // ✅ REJECT는 failure로 보지 않는 편(분석 일관성)
                finishedAtMillis,
                null,
                null
        );
    }

    /**
     * PROCESSING -> FAILED_FINAL
     * - 실패는 분석을 위해 errorCode/errorMessage도 남긴다(선택)
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

    /**
     * 최종 상태 공통 업데이트
     * - Condition: status = PROCESSING
     * - Update: status/finishedAt/uiResult/resultCode(+ failureClass?) (+ errorCode/errorMessage?)
     */
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
                ATTR_PK, AttributeValue.builder().s(DdbKeyFactory.requestPk(requestId)).build(),
                ATTR_SK, AttributeValue.builder().s(DdbKeyFactory.metaSk()).build()
        );

        // DDB 안전: 너무 긴 메시지는 잘라서 저장(아이템 사이즈/로그 폭주 방지)
        String safeErrorMessage = truncate(errorMessage, 500);

        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();

        names.put("#status", "status");
        names.put("#finishedAt", "finishedAt");
        names.put("#uiResult", "uiResult");
        names.put("#resultCode", "resultCode");

        values.put(":processing", AttributeValue.builder().s("PROCESSING").build());
        values.put(":target", AttributeValue.builder().s(targetStatus).build());
        values.put(":finishedAt", AttributeValue.builder().n(String.valueOf(finishedAtMillis)).build());
        values.put(":uiResult", AttributeValue.builder().s(uiResult).build());
        values.put(":resultCode", AttributeValue.builder().s(resultCode.name()).build());

        String updateExpr =
                "SET #status = :target, #finishedAt = :finishedAt, #uiResult = :uiResult, #resultCode = :resultCode";

        if (failureClass != null) {
            names.put("#failureClass", "failureClass");
            values.put(":failureClass", AttributeValue.builder().s(failureClass.name()).build());
            updateExpr += ", #failureClass = :failureClass";
        }

        if (errorCode != null && !errorCode.isBlank()) {
            names.put("#errorCode", "errorCode");
            values.put(":errorCode", AttributeValue.builder().s(errorCode).build());
            updateExpr += ", #errorCode = :errorCode";
        }

        if (safeErrorMessage != null && !safeErrorMessage.isBlank()) {
            names.put("#errorMessage", "errorMessage");
            values.put(":errorMessage", AttributeValue.builder().s(safeErrorMessage).build());
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
     */
    public Optional<String> getCurrentStatus(String requestId) {
        Map<String, AttributeValue> key = Map.of(
                ATTR_PK, AttributeValue.builder().s(DdbKeyFactory.requestPk(requestId)).build(),
                ATTR_SK, AttributeValue.builder().s(DdbKeyFactory.metaSk()).build()
        );

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .projectionExpression("#status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item = dynamoDbClient.getItem(request).item();

        if (item == null || !item.containsKey("status")) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.get("status")).map(AttributeValue::s);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
