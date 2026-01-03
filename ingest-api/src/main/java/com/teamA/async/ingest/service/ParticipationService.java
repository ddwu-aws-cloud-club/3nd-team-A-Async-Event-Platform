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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            return new ParticipationResponse(existing, true);
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

        try {
            // 3) SQS enqueue
            ParticipationMessage msg =
                    new ParticipationMessage(newRequestId, eventId, EventType.FIRST_COME);

            String body = objectMapper.writeValueAsString(msg);

            sqsClient.sendMessage(r -> r
                    .queueUrl(queueUrl)
                    .messageBody(body)
            );

            // 4) enqueue 성공 → RECEIVED → QUEUED
            long queuedAt = System.currentTimeMillis();

            Map<String, Object> patch = new HashMap<>();
            patch.put("queuedAt", queuedAt);
            patch.put("GSI1PK", DdbKeyFactory.userPk(userId));
            patch.put("GSI1SK", DdbKeyFactory.userRequestSk(queuedAt, newRequestId));
            patch.put("GSI2PK", DdbKeyFactory.eventPk(eventId));
            patch.put("GSI2SK", DdbKeyFactory.eventRequestSk(queuedAt, newRequestId));

            stateTransitionService.transition(
                    newRequestId,
                    RequestStatus.RECEIVED,
                    RequestStatus.QUEUED,
                    patch
            );

        } catch (Exception e) {
            // 5) enqueue 실패 → FAILED_FINAL
            long nowFail = System.currentTimeMillis();

            Map<String, Object> patch = new HashMap<>();
            patch.put("resultCode", ResultCode.FAILED_INGEST_ENQUEUE);
            patch.put("uiResult", UiResult.FAILED);
            patch.put("finishedAt", nowFail);
            patch.put("errorMessage", e.getMessage());

            stateTransitionService.transition(
                    newRequestId,
                    RequestStatus.RECEIVED,
                    RequestStatus.FAILED_FINAL,
                    patch
            );
        }


        return new ParticipationResponse(newRequestId, false);
    }

    private String newRequestId() {
        // 형식은 팀 규칙대로. 우선 UUID short 형태
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
