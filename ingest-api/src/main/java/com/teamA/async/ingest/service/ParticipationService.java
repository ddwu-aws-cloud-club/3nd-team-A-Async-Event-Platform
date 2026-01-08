package com.teamA.async.ingest.service;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.common.transition.StateTransitionService;
import com.teamA.async.ingest.api.dto.ParticipationResponse;
import com.teamA.async.ingest.ddb.IdempotencyRepository;
import com.teamA.async.ingest.ddb.RequestWriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationService {

    private final IdempotencyRepository idempotencyRepository;
    private final RequestWriteRepository requestWriteRepository;

    private final SqsClient sqsClient;
    private final StateTransitionService stateTransitionService;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    // 시간은 우선 System.currentTimeMillis()로 가고, 나중에 common Clock으로 교체해도 됨
    public ParticipationResponse participate(String eventId, String userId) {
        String idempotencyPk = DdbKeyFactory.idempotencyPk(eventId, userId);
        String requestId;
        boolean isDuplicate;

        // 1) lock 성공 후 requestId 생성하도록 리팩터링
        String newRequestId = newRequestId();
        boolean locked = idempotencyRepository.tryLock(idempotencyPk, newRequestId);

        // 2-A) Lock 실패 => 기존 requestId 반환 (새 requestId 생성 금지 정책 충족)
        if (!locked) {
            String existing = idempotencyRepository.getRequestId(idempotencyPk);
            // 여기서 existing이 null이면 데이터 이상 케이스인데, G0에선 일단 예외로 터뜨려도 OK
            if (existing == null) {
                throw new IllegalStateException("Idempotency lock exists but requestId missing: " + idempotencyPk);
            }
            // G1-Step6: 여기서 SQS로의 메시지를 막음
            // return new ParticipationResponse(existing, true);

            requestId = existing;
            isDuplicate = true;

            // ⚠️ 여기서는 RequestItem을 새로 만들지 않음 (이미 RECEIVED/QUEUED/그 이후 상태로 존재하니까)
        } else {
            // 최초 요청
            requestId = newRequestId;
            isDuplicate = false;

            long now = System.currentTimeMillis();
            RequestItem item = RequestItem.builder()
                    .requestId(requestId)
                    .eventId(eventId)
                    .userId(userId)
                    .status(RequestStatus.RECEIVED)
                    .requestedAt(now)
                    .build();

            requestWriteRepository.putReceived(item);
        }

        // 2-B) Lock 성공 => RequestItem (RECEIVED) 생성 (GSI 미세팅)
        long now = System.currentTimeMillis();
        RequestItem item = RequestItem.builder()
                .requestId(newRequestId)
                .eventId(eventId)
                .userId(userId)
                .status(RequestStatus.RECEIVED)
                .requestedAt(now)
                .build();

        // ❗ G0 규칙: RECEIVED 단계에서는 GSI 세팅하지 않음
        // 지금 RequestItem.generateKeys()는 GSI도 생성해버리니까, "RECEIVED 전용 키 생성"을 분리하는 걸 추천.
        // 일단 여기서는 base key만 set하도록 repository에서 강제한다.
        requestWriteRepository.putReceived(item);

        // 중복이든 아니든 SQS로 보내기
        try {
            // 3) SQS enqueue 전에 queuedAt 확정 (Worker 생성 금지 규칙)
            long queuedAt = System.currentTimeMillis();

            ParticipationMessage msg = new ParticipationMessage(
                    newRequestId, eventId, userId, queuedAt, EventType.FIRST_COME
            );

            String body = objectMapper.writeValueAsString(msg);

            Map<String, MessageAttributeValue> attributes = Map.of(
                    "requestId", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(newRequestId)
                            .build(),
                    "eventId", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(eventId)
                            .build(),
                    "userId", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(userId)
                            .build(),
                    "eventType", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(EventType.FIRST_COME.name())
                            .build(),
                    "queuedAt", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(Long.toString(queuedAt))
                            .build()
            );

            sqsClient.sendMessage(r -> r
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageAttributes(attributes)
            );

            // 최초 요청일 때만 RECEIVED -> QUEUED 전이
            if (!isDuplicate) {
                Map<String, Object> patch = new HashMap<>();
                patch.put("queuedAt", queuedAt);
                patch.put("GSI1PK", DdbKeyFactory.userPk(userId));
                patch.put("GSI1SK", DdbKeyFactory.userRequestSk(queuedAt, requestId));
                patch.put("GSI2PK", DdbKeyFactory.eventPk(eventId));
                patch.put("GSI2SK", DdbKeyFactory.eventRequestSk(queuedAt, requestId));

                stateTransitionService.transition(
                        requestId,
                        RequestStatus.RECEIVED,
                        RequestStatus.QUEUED,
                        patch
                );
            }

            return new ParticipationResponse(requestId, isDuplicate);

        } catch (Exception e) {
            // SQS 전송 실패 시
            long nowFail = System.currentTimeMillis();

            // 중복 요청일 때는 이미 존재하는 RequestItem이 있으므로 FAILED_FINAL 불가
            if (isDuplicate) {
                log.warn("SQS enqueue failed for duplicate request (non-critical). eventId={}, userId={}, existingRequestId={}",
                        eventId, userId, requestId, e);
                // 중복 요청이니 그냥 클라이언트에 성공 응답 내려줌 (이미 처리된 건이니까)
                return new ParticipationResponse(requestId, true);
            }

            // 최초 요청이고 SQS 전송 실패 → FAILED_FINAL로 마무리
            Map<String, Object> patch = new HashMap<>();
            patch.put("resultCode", ResultCode.FAILED_INGEST_ENQUEUE);
            patch.put("uiResult", UiResult.FAILED);
            patch.put("finishedAt", nowFail);
            patch.put("errorMessage", "SQS enqueue failed: " + e.getMessage());

            stateTransitionService.transition(
                    requestId,
                    RequestStatus.RECEIVED,
                    RequestStatus.FAILED_FINAL,
                    patch
            );

            log.error("SQS enqueue failed for new request. requestId={}, eventId={}, userId={}",
                    requestId, eventId, userId, e);

            return new ParticipationResponse(requestId, false); // isDuplicate는 false 그대로
        }
    }

    private String newRequestId () {
        // 형식은 팀 규칙대로. 우선 UUID short 형태
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }
}