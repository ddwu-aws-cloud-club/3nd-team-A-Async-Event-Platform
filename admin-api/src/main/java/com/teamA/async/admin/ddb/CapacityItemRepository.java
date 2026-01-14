package com.teamA.async.admin.ddb;

import com.teamA.async.admin.ddb.keys.AdminDdbKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CapacityItemRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    // Worker가 쓰는 SK와 반드시 동일해야 함
    private static final String CAPACITY_SK = "CONFIG";

    public void putInitialCapacityForFirstCome(String eventId, long capacityTotal, long nowEpochMs) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(AdminDdbKeyFactory.eventPk(eventId)).build());
        item.put("SK", AttributeValue.builder().s(CAPACITY_SK).build());

        // Worker 필드명과 일치
        item.put("capacityRemaining", AttributeValue.builder().n(Long.toString(capacityTotal)).build());
        item.put("updatedAt", AttributeValue.builder().n(Long.toString(nowEpochMs)).build());

        // (선택) 운영 편의
        item.put("capacityTotal", AttributeValue.builder().n(Long.toString(capacityTotal)).build());
        item.put("createdAt", AttributeValue.builder().n(Long.toString(nowEpochMs)).build());

        // 중복 생성 방지 (같은 eventId로 재생성 방지)
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build());
    }
}
