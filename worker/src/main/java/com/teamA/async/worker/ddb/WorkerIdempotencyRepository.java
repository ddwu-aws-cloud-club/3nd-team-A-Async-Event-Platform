package com.teamA.async.worker.ddb;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class WorkerIdempotencyRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    public boolean tryLock(String eventId, String userId, String requestId) {

        String pk = "IDEMP#" + eventId + "#" + userId;

        Map<String, AttributeValue> item = Map.of(
                "PK", AttributeValue.fromS(pk),
                "SK", AttributeValue.fromS("LOCK"),
                "requestId", AttributeValue.fromS(requestId)
        );

        PutItemRequest req = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) OR #requestId = :rid") //dlq 전략수정
                .expressionAttributeNames(Map.of("#requestId", "requestId"))
                .expressionAttributeValues(Map.of(":rid", AttributeValue.fromS(requestId)))
                .build();

        try {
            dynamoDbClient.putItem(req);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
