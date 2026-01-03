package com.teamA.async.ingest.api;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.dto.MyParticipationItem;
import com.teamA.async.common.domain.dto.RequestStatusResponse;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class RequestQueryController {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    // 1. 단건 조회 (Polling용)
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<RequestStatusResponse> getRequestStatus(@PathVariable String requestId) {
        Key key = Key.builder()
                .partitionValue(DdbKeyFactory.requestPk(requestId))
                .sortValue(DdbKeyFactory.metaSk())
                .build();

        RequestItem item = enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class))
                .getItem(r -> r.key(key).consistentRead(true));

        if (item == null) return ResponseEntity.notFound().build();

        UiResult uiResult = item.getUiResult() == null ? UiResult.PENDING : item.getUiResult();

        return ResponseEntity.ok(RequestStatusResponse.builder()
                .requestId(item.getRequestId())
                .eventId(item.getEventId())
                .eventType(item.getEventType())
                .status(item.getStatus())
                .uiResult(uiResult)
                .resultCode(item.getResultCode())
                .timestamps(RequestStatusResponse.Timestamps.builder()
                        .requestedAt(item.getRequestedAt())
                        .queuedAt(item.getQueuedAt())
                        .startedAt(item.getStartedAt())
                        .finishedAt(item.getFinishedAt())
                        .build())
                .build());
    }

    // 2. 내 신청 내역 조회 (목록용, GSI1 사용)
    @GetMapping("/me/participations")
    public ResponseEntity<List<MyParticipationItem>> getMyParticipations(
            @RequestParam(defaultValue = "20") int limit,
            // 실제로는 JWT 토큰에서 userId를 꺼내야 함 (임시로 파라미터 처리)
            @RequestParam String userId
    ) {
        // GSI1 PK 생성: USER#{userId}
        Key gsiKey = Key.builder()
                .partitionValue(DdbKeyFactory.userPk(userId))
                .build();

        // GSI 조회 (최신순 정렬)
        Page<RequestItem> page = enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class))
                .index("GSI1")
                .query(q -> q.queryConditional(QueryConditional.keyEqualTo(gsiKey))
                        .scanIndexForward(false) // 내림차순 (최신순)
                        .limit(limit))
                .iterator()
                .next();

        List<MyParticipationItem> items = page.items().stream()
                .map(MyParticipationItem::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }
}
