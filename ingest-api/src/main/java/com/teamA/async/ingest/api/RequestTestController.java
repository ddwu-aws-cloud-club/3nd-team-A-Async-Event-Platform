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
    public RequestItem createTestRequest(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        String eventId = (String) body.get("eventId");
        // "isQueued": true 옵션을 받을 수 있음
        boolean isQueued = Boolean.TRUE.equals(body.get("isQueued"));

        String requestId = UUID.randomUUID().toString();

        // 1. 기본 객체 생성 (RECEIVED)
        RequestItem item = RequestItem.builder()
                .userId(userId)
                .eventId(eventId)
                .requestId(requestId)
                .status(RequestStatus.RECEIVED)
                .requestedAt(System.currentTimeMillis())
                .build();

        // 2. Base 키 생성 (항상 수행)
        item.generateBaseKeys();

        // 3. 옵션: QUEUED 상태로 만들고 싶다면?
        if (isQueued) {
            item.setStatus(RequestStatus.QUEUED);
            item.setQueuedAt(System.currentTimeMillis());
            // 이때만 GSI 키를 생성! -> 목록 조회 API에 노출됨
            item.generateGsiKeys();
        }

        DynamoDbTable<RequestItem> table = enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class));
        table.putItem(item);

        return item;
    }
}
