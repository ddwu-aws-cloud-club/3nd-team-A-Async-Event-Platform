package com.teamA.async.ingest.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.model.RequestItem;
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
public class RequestWriteRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    public void putReceived(RequestItem item) {
        if (item.getRequestId() == null || item.getEventId() == null || item.getUserId() == null) {
            throw new IllegalStateException("requestId/eventId/userId must not be null");
        }

        // ✅ RECEIVED에서는 base key만 세팅 (GSI는 세팅 금지)
        item.setPk(DdbKeyFactory.requestPk(item.getRequestId()));
        item.setSk(DdbKeyFactory.metaSk());

        Map<String, AttributeValue> map = new HashMap<>();
        map.put("PK", AttributeValue.builder().s(item.getPk()).build());
        map.put("SK", AttributeValue.builder().s(item.getSk()).build());

        // 도메인 필드
        map.put("requestId", AttributeValue.builder().s(item.getRequestId()).build());
        map.put("eventId", AttributeValue.builder().s(item.getEventId()).build());
        map.put("userId", AttributeValue.builder().s(item.getUserId()).build());
        map.put("status", AttributeValue.builder().s(item.getStatus().name()).build());
        map.put("requestedAt", AttributeValue.builder().n(Long.toString(item.getRequestedAt())).build());

        // ❌ GSI1PK/GSI1SK/GSI2PK/GSI2SK는 절대 넣지 않음 (RECEIVED 단계 규칙)

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(map)
                .build());
    }
}
