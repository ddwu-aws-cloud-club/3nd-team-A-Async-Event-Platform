package com.teamA.async.ingest.api;

import com.teamA.async.common.domain.model.RequestItem;
import com.teamA.async.common.domain.enums.RequestStatus;
import lombok.RequiredArgsConstructor;
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
                .queuedAt(System.currentTimeMillis()) // 현재 시간
                .build();

        // ✅ 3. 핵심: 여기서 키 생성 로직이 잘 도는지 테스트됨!
        item.generateKeys();

        // 4. DynamoDB에 저장 (테이블 이름: AsyncEventTable 가정)
        DynamoDbTable<RequestItem> table = enhancedClient.table("AsyncEventTable", TableSchema.fromBean(RequestItem.class));
        table.putItem(item);

        // 5. 결과 반환 (PK, SK가 잘 채워졌는지 JSON으로 확인)
        return item;
    }
}
