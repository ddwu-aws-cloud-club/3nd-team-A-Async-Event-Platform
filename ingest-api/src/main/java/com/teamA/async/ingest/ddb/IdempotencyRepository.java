package com.teamA.async.ingest.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    private static final String ATTR_PK = "PK";
    private static final String ATTR_SK = "SK";
    private static final String ATTR_REQUEST_ID = "requestId";

    public boolean tryLock(String idempotencyPk, String requestId) {
        Map<String, AttributeValue> item = Map.of(
                ATTR_PK, AttributeValue.builder().s(idempotencyPk).build(),
                ATTR_SK, AttributeValue.builder().s(DdbKeyFactory.lockSk()).build(),
                ATTR_REQUEST_ID, AttributeValue.builder().s(requestId).build()
        );

        // attribute_not_exists(PK) AND attribute_not_exists(SK)
        // (PK만으로도 충분한데, 문서 규칙 그대로 반영)
        PutItemRequest req = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                .expressionAttributeNames(Map.of("#pk", ATTR_PK, "#sk", ATTR_SK))
                .build();

        try {
            dynamoDbClient.putItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public String getRequestId(String idempotencyPk) {
        Map<String, AttributeValue> key = Map.of(
                ATTR_PK, AttributeValue.builder().s(idempotencyPk).build(),
                ATTR_SK, AttributeValue.builder().s(DdbKeyFactory.lockSk()).build()
        );

        GetItemResponse res = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build());

        if (!res.hasItem()) return null;

        AttributeValue v = res.item().get(ATTR_REQUEST_ID);
        return v == null ? null : v.s();
    }
}
