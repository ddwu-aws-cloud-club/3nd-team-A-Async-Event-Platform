package com.teamA.async.admin.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RequestItemRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    public List<RequestItem> findPendingByEvent(String eventId) {

        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI2")
                .keyConditionExpression("GSI2PK = :pk")
                .filterExpression("#st IN (:queued, :processing)")
                .expressionAttributeNames(Map.of(
                        "#st", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder()
                                .s(DdbKeyFactory.eventPk(eventId))
                                .build(),
                        ":queued", AttributeValue.builder()
                                .s("QUEUED")
                                .build(),
                        ":processing", AttributeValue.builder()
                                .s("PROCESSING")
                                .build()
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(request);

        List<RequestItem> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            result.add(DynamoDbItemMapper.toRequestItem(item));
        }
        return result;
    }

}
