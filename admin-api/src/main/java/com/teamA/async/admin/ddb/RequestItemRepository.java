package com.teamA.async.admin.ddb;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RequestItemRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${ddb.table-name}")
    private String tableName;

    /**
     * ✅ eventId 기준으로 "해당 이벤트의 RequestItem 전부" 조회
     * - draw 시점에 이미 SUCCEEDED가 찍혀있어도 WIN/LOSE로 덮어쓰기 위해 필요
     */
    public List<RequestItem> findByEvent(String eventId) {

        List<RequestItem> result = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            QueryRequest.Builder b = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("GSI2")
                    .keyConditionExpression("GSI2PK = :pk")
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder()
                                    .s(DdbKeyFactory.eventPk(eventId))
                                    .build()
                    ));

            if (lastKey != null && !lastKey.isEmpty()) {
                b.exclusiveStartKey(lastKey);
            }

            QueryResponse response = dynamoDbClient.query(b.build());

            for (Map<String, AttributeValue> item : response.items()) {
                RequestItem r = DynamoDbItemMapper.toRequestItem(item);

                // ✅ 🔒 PK / SK 강제 보정 (draw 안전성 핵심)
                if (r.getPk() == null) {
                    r.setPk(item.get("PK").s());
                }
                if (r.getSk() == null) {
                    r.setSk(item.get("SK").s());
                }

                result.add(r);
            }

            lastKey = response.lastEvaluatedKey();
        } while (lastKey != null && !lastKey.isEmpty());

        return result;
    }
}
