package com.teamA.async.ingest.api;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.dto.MyParticipationItem;
import com.teamA.async.common.domain.dto.RequestStatusResponse;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class RequestQueryController {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

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

    @GetMapping("/me/participations")
    public ResponseEntity<List<MyParticipationItem>> getMyParticipations(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam String userId
    ) {
        Key gsiKey = Key.builder()
                .partitionValue(DdbKeyFactory.userPk(userId))
                .build();

        // (선택) 안전장치 필터: queuedAt 존재 + RECEIVED 제외
        // 사실상 GSI1이 QUEUED 때만 세팅되면 없어도 됨.
        Expression filter = Expression.builder()
                .expression("attribute_exists(#queuedAt) AND #status <> :received")
                .expressionNames(Map.of(
                        "#queuedAt", "queuedAt",
                        "#status", "status"
                ))
                .expressionValues(Map.of(
                        ":received", AttributeValue.builder().s("RECEIVED").build()
                ))
                .build();

        Iterator<Page<RequestItem>> it = enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class))
                .index("GSI1")
                .query(q -> q.queryConditional(QueryConditional.keyEqualTo(gsiKey))
                        .scanIndexForward(false)
                        .limit(limit)
                        .filterExpression(filter)
                )
                .iterator();

        if (!it.hasNext()) {
            return ResponseEntity.ok(List.of());
        }

        Page<RequestItem> page = it.next();

        List<MyParticipationItem> items = page.items().stream()
                .map(MyParticipationItem::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }
}