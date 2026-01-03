package com.teamA.async.common.transition;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.enums.RequestStatus;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class DynamoStateTransitionService implements StateTransitionService {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    private static final String ATTR_PK = "PK";
    private static final String ATTR_SK = "SK";
    private static final String ATTR_STATUS = "status";

    @Override
    public TransitionResult transition(
            String requestId,
            RequestStatus from,
            RequestStatus to,
            Map<String, Object> patchFields
    ) {
        // 1) 상태 전이 규칙 검증 (G0 고정)
        if (!StateTransitionRules.isAllowed(from, to)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + from + " -> " + to
            );
        }

        // 2) PK / SK 생성 (절대 문자열 하드코딩 금지)
        Map<String, AttributeValue> key = Map.of(
                ATTR_PK, AttributeValue.builder()
                        .s(DdbKeyFactory.requestPk(requestId))
                        .build(),
                ATTR_SK, AttributeValue.builder()
                        .s(DdbKeyFactory.metaSk())
                        .build()
        );

        // 3) UpdateExpression 구성
        UpdateParts parts = buildUpdateParts(to, patchFields);

        // 4) ConditionExpression: status == from (G0 고정)
        Map<String, String> names = new HashMap<>(parts.names);
        names.put("#status", ATTR_STATUS);

        Map<String, AttributeValue> values = new HashMap<>(parts.values);
        values.put(":fromStatus", AttributeValue.builder().s(from.name()).build());

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(parts.updateExpression)
                .conditionExpression("#status = :fromStatus")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        try {
            dynamoDbClient.updateItem(req);
            return new TransitionResult.Success();
        } catch (ConditionalCheckFailedException e) {
            return new TransitionResult.ConditionFailed();
        }
    }

    /**
     * status는 항상 to 값으로 갱신
     * patchFields에는 status를 넣을 수 없음
     */
    private UpdateParts buildUpdateParts(
            RequestStatus to,
            Map<String, Object> patchFields
    ) {
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();

        StringBuilder set = new StringBuilder("SET ");

        // status
        names.put("#status", ATTR_STATUS);
        values.put(":toStatus", AttributeValue.builder().s(to.name()).build());
        set.append("#status = :toStatus");

        if (patchFields == null || patchFields.isEmpty()) {
            return new UpdateParts(set.toString(), names, values);
        }

        int i = 0;
        for (Map.Entry<String, Object> e : patchFields.entrySet()) {
            String field = e.getKey();
            Object raw = e.getValue();

            if ("status".equals(field)) {
                throw new IllegalArgumentException(
                        "Do not patch 'status' directly. Use transition(to)."
                );
            }

            String nKey = "#f" + i;
            String vKey = ":v" + i;

            names.put(nKey, field);
            values.put(vKey, toAttrValue(raw));

            set.append(", ").append(nKey).append(" = ").append(vKey);
            i++;
        }

        return new UpdateParts(set.toString(), names, values);
    }

    /**
     * G0에서 필요한 타입만 지원
     */
    private AttributeValue toAttrValue(Object raw) {
        if (raw == null) {
            return AttributeValue.builder().nul(true).build();
        }
        if (raw instanceof String v) {
            return AttributeValue.builder().s(v).build();
        }
        if (raw instanceof Boolean v) {
            return AttributeValue.builder().bool(v).build();
        }
        if (raw instanceof Integer v) {
            return AttributeValue.builder().n(Integer.toString(v)).build();
        }
        if (raw instanceof Long v) {
            return AttributeValue.builder().n(Long.toString(v)).build();
        }
        if (raw instanceof Enum<?> e) {
            return AttributeValue.builder().s(e.name()).build();
        }

        throw new IllegalArgumentException(
                "Unsupported attribute type: " + raw.getClass()
        );
    }

    private record UpdateParts(
            String updateExpression,
            Map<String, String> names,
            Map<String, AttributeValue> values
    ) {}
}

