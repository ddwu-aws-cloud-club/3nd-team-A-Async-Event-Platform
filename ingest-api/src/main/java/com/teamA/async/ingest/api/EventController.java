package com.teamA.async.ingest.api;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.model.EventItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    /**
     * GET /events
     * 모든 이벤트 목록 조회
     */
    @GetMapping("api/events")
    public ResponseEntity<List<EventItem>> getEvents(@RequestParam(defaultValue = "20") int limit) {
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .filterExpression(Expression.builder()
                        .expression("begins_with(PK, :pkPrefix) AND SK = :skValue")
                        .putExpressionValue(":pkPrefix", AttributeValue.builder().s("EVENT#").build())
                        .putExpressionValue(":skValue", AttributeValue.builder().s("CONFIG").build())
                        .build())
                .build();

        // 💡 포인트: PageIterable을 사용하여 데이터가 발견될 때까지 전체를 순회함
        List<EventItem> allEvents = enhancedClient
                .table(tableName, TableSchema.fromBean(EventItem.class))
                .scan(scanRequest)
                .items() // 모든 페이지를 알아서 넘기며 아이템을 스트림으로 제공
                .stream()
                .limit(limit) // 찾은 결과 중 limit만큼만 필터링
                .collect(Collectors.toList());

        return ResponseEntity.ok(allEvents);
    }

    /**
     * GET /events/{id}
     * 특정 이벤트 상세 조회
     */
    @GetMapping("api/events/{id}")
    public ResponseEntity<EventItem> getEvent(@PathVariable String id) {
        Key key = Key.builder()
                .partitionValue(DdbKeyFactory.eventPk(id))
                .sortValue("CONFIG") // 또는 적절한 SK
                .build();

        EventItem item = enhancedClient
                .table(tableName, TableSchema.fromBean(EventItem.class))
                .getItem(r -> r.key(key).consistentRead(true));

        if (item == null) {
            // 로그를 남겨주면 디버깅이 훨씬 편해집니다.
            System.out.println("Event not found for ID: " + id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(item);
    }


}