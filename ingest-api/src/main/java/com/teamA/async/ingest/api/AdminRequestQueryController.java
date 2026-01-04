package com.teamA.async.ingest.api;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.dto.AdminEventRequestItem;
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
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminRequestQueryController {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 100;

    private DynamoDbTable<RequestItem> table() {
        return enhancedClient.table(tableName, TableSchema.fromBean(RequestItem.class));
    }

    // 1) Admin 단건 조회
    @GetMapping("/admin/requests/{requestId}")
    public ResponseEntity<RequestStatusResponse> getAdminRequest(@PathVariable String requestId) {
        Key key = Key.builder()
                .partitionValue(DdbKeyFactory.requestPk(requestId))
                .sortValue(DdbKeyFactory.metaSk())
                .build();

        RequestItem item = table().getItem(r -> r.key(key).consistentRead(true));
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

    // 2) Admin 이벤트별 목록 조회 (GSI2)
    @GetMapping("/admin/events/{eventId}/requests")
    public ResponseEntity<List<AdminEventRequestItem>> getAdminEventRequests(
            @PathVariable String eventId,
            @RequestParam(required = false) Integer limit
    ) {
        int lim = clampLimit(limit);

        Key gsiKey = Key.builder()
                .partitionValue(DdbKeyFactory.eventPk(eventId))
                .build();

        List<AdminEventRequestItem> result = new ArrayList<>(lim);

        Iterator<Page<RequestItem>> it = table()
                .index("GSI2")
                .query(q -> q.queryConditional(QueryConditional.keyEqualTo(gsiKey))
                        .scanIndexForward(false)
                        .limit(lim))
                .iterator();

        while (it.hasNext() && result.size() < lim) {
            Page<RequestItem> page = it.next();
            for (RequestItem item : page.items()) {
                result.add(AdminEventRequestItem.from(item));
                if (result.size() >= lim) break;
            }
        }

        return ResponseEntity.ok(result);
    }

    private int clampLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1) return 1;
        return Math.min(limit, MAX_LIMIT);
    }
}

