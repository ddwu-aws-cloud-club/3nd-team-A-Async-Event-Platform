package com.teamA.async.admin.service;

import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RequestItemResultWriter {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    private static final String RC_LOTTERY_WIN  = "LOTTERY_WIN";
    private static final String RC_LOTTERY_LOST = "LOTTERY_LOST";

    public void markWinners(List<RequestItem> items) {
        long now = System.currentTimeMillis();
        items.forEach(r ->
                updateResult(r, RequestStatus.SUCCEEDED, UiResult.SUCCESS, RC_LOTTERY_WIN, now)
        );
    }

    public void markLosers(List<RequestItem> items) {
        long now = System.currentTimeMillis();
        items.forEach(r ->
                updateResult(r, RequestStatus.REJECTED, UiResult.REJECTED, RC_LOTTERY_LOST, now)
        );
    }

    private void updateResult(RequestItem r,
                              RequestStatus status,
                              UiResult uiResult,
                              String resultCode,
                              long now) {

        // 🔒 1. PK / SK 필수 검증
        if (r.getPk() == null || r.getSk() == null) {
            throw new IllegalStateException(
                    "RequestItem PK/SK is null. requestId=" + r.getRequestId()
            );
        }

        // 🔒 2. enum / code null 방어
        if (status == null || uiResult == null || resultCode == null) {
            throw new IllegalStateException(
                    "Invalid result fields. status=" + status +
                            ", uiResult=" + uiResult +
                            ", resultCode=" + resultCode
            );
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.builder().s(r.getPk()).build(),
                        "SK", AttributeValue.builder().s(r.getSk()).build()
                ))
                .updateExpression(
                        "SET #status = :s, #ui = :ui, #code = :c, #finishedAt = :now"
                )
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#ui", "uiResult",
                        "#code", "resultCode",
                        "#finishedAt", "finishedAt"
                ))
                .expressionAttributeValues(Map.of(
                        ":s", AttributeValue.builder().s(status.name()).build(),
                        ":ui", AttributeValue.builder().s(uiResult.name()).build(),
                        ":c", AttributeValue.builder().s(resultCode).build(),
                        ":now", AttributeValue.builder().n(Long.toString(now)).build()
                ))
                .build());
    }
}
