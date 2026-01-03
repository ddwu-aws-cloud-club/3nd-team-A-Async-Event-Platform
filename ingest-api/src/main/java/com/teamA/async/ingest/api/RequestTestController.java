package com.teamA.async.ingest.api;

import com.teamA.async.common.domain.model.RequestItem;
import com.teamA.async.common.domain.enums.RequestStatus;
import lombok.RequiredArgsConstructor;
// import lombok.Value;  <-- 이거 지우세요!
import org.springframework.beans.factory.annotation.Value; // <-- 이걸 추가하세요!
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RequestTestController {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    // Postman에서 호출할 주소: POST http://localhost:8080/test/requests
    @PostMapping("/test/requests")
    public RequestItem createTestRequest(@RequestBody Map<String, String> body) {
        // 1. Postman Body에서 데이터 꺼내기
        String userId = body.get("userId");
        String eventId = body.get("eventId");
        String requestId = UUID.randomUUID().toString(); // 랜덤 생성

        // 2. RequestItem 객체 생성 (Builder 패턴)
        RequestItem item = RequestItem.builder()
                .userId(userId)
                .eventId(eventId)
                .requestId(requestId)
                .status(RequestStatus.RECEIVED)
                .requestedAt(System.currentTimeMillis())
                .build();

        // 3. 키 생성
        item.generateKeys();

        // 4. DynamoDB에 저장
        DynamoDbTable<RequestItem> table = enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class));
        table.putItem(item);

        // 5. 결과 반환
        return item;
    }
}
