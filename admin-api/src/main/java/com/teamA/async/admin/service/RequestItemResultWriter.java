package com.teamA.async.admin.service;

import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
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

    /**
     * LOTTERY 당첨 처리
     * - status: SUCCEEDED
     * - uiResult: SUCCESS
     * - resultCode: SUCCESS
     */
    public void markWinners(List<RequestItem> items) {
        items.forEach(r ->
                updateResult(
                        r,
                        RequestStatus.SUCCEEDED,
                        UiResult.SUCCESS,
                        ResultCode.SUCCESS
                )
        );
    }

    /**
     * LOTTERY 탈락 처리
     * - status: REJECTED
     * - uiResult: REJECTED
     * - resultCode: REJECTED_CAPACITY
     */
    public void markLosers(List<RequestItem> items) {
        items.forEach(r ->
                updateResult(
                        r,
                        RequestStatus.REJECTED,
                        UiResult.REJECTED,
                        ResultCode.REJECTED_CAPACITY
                )
        );
    }

    private void updateResult(RequestItem r,
                              RequestStatus status,
                              UiResult uiResult,
                              ResultCode code) {

        long now = System.currentTimeMillis();

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
                        ":c", AttributeValue.builder().s(code.name()).build(),
                        ":now", AttributeValue.builder().n(Long.toString(now)).build()
                ))
                .build());
    }
}
