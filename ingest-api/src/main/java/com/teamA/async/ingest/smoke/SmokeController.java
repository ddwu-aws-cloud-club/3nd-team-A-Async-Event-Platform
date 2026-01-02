package com.teamA.async.ingest.smoke;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/__smoke")
@RequiredArgsConstructor
public class SmokeController {

    private final DynamoDbClient ddb;
    private final SqsClient sqs;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    /**
     * 1) PutItem (RECEIVED) -> 2) GetItem -> 3) Conditional Update (RECEIVED->QUEUED) + retry fail
     */
    @PostMapping("/ddb")
    public ResponseEntity<?> ddbSmoke() {
        String requestId = "SMOKE-" + UUID.randomUUID();
        long now = Instant.now().toEpochMilli();

        String pk = DdbKeyFactory.requestPk(requestId);
        String sk = "META";

        // 1) PutItem (status=RECEIVED)
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.fromS(pk));
        item.put("SK", AttributeValue.fromS(sk));
        item.put("status", AttributeValue.fromS("RECEIVED"));
        item.put("requestedAt", AttributeValue.fromN(String.valueOf(now)));

        ddb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        // 2) GetItem
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.fromS(pk),
                "SK", AttributeValue.fromS(sk)
        );

        GetItemResponse got = ddb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build());

        if (!got.hasItem()) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "reason", "GetItem returned empty"
            ));
        }

        // 3) Conditional Update: status == RECEIVED 일 때만 QUEUED로
        long queuedAt = Instant.now().toEpochMilli();

        UpdateItemRequest firstUpdate = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("#s = :received")
                .updateExpression("SET #s = :queued, queuedAt = :qat")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":received", AttributeValue.fromS("RECEIVED"),
                        ":queued", AttributeValue.fromS("QUEUED"),
                        ":qat", AttributeValue.fromN(String.valueOf(queuedAt))
                ))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        UpdateItemResponse updated = ddb.updateItem(firstUpdate);

        // 3-2) 동일 update 재시도 -> 조건 실패 확인
        boolean secondUpdateConditionFailed;
        try {
            ddb.updateItem(firstUpdate);
            secondUpdateConditionFailed = false; // 여기 오면 이상함 (조건이 안 먹는 상태)
        } catch (ConditionalCheckFailedException e) {
            secondUpdateConditionFailed = true; // 기대값
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "requestId", requestId,
                "put", "OK",
                "get_status", got.item().get("status").s(),
                "first_update_new_status", updated.attributes().get("status").s(),
                "second_update_condition_failed", secondUpdateConditionFailed
        ));
    }

    /**
     * SQS SendMessage 성공 + messageId 확인
     */
    @PostMapping("/sqs")
    public ResponseEntity<?> sqsSmoke() {
        String body = "{\"type\":\"SMOKE\",\"ts\":" + Instant.now().toEpochMilli() + "}";

        SendMessageResponse res = sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build());

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "messageId", res.messageId()
        ));
    }
}
