package com.teamA.async.ingest.service;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import com.teamA.async.common.messaging.ParticipationMessage;
import com.teamA.async.common.transition.StateTransitionService;
import com.teamA.async.common.transition.TransitionResult;
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
        // ✅ [문제 1] requestId 단일화: newRequestId를 "절대 사용하지 않는 임시 후보"로만 쓰고,
        //              participate() 내부의 "실제 ID"는 requestId 하나로만 통일
        String candidateRequestId = newRequestId();
        boolean locked = idempotencyRepository.tryLock(idempotencyPk, candidateRequestId);

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
            // ✅ [문제 5] 중복 요청은 SQS/상태전이(QUEUED) 재수행 금지
            // - 중복인데도 아래 try 블록에서 QUEUED 전이/메시지 enqueue를 시도하면
            //   requestId 상태와 SQS 메시지 불일치/중복이 다시 생길 수 있음
            return new ParticipationResponse(requestId, true);
        } else {
            // 최초 요청
            requestId = candidateRequestId;
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

        // ✅ [문제 2] RequestItem 중복 생성 제거:
        // 기존 코드의 "2-B) Lock 성공 => RequestItem (RECEIVED) 생성" 블록은
        // requestId를 다시 만들거나(또는 다른 변수로) RECEIVED를 2번 쓰는 위험이 있어서 제거함.

        // ✅ [문제 4] RECEIVED→QUEUED 전이를 SQS enqueue보다 먼저 수행해서
        // 워커가 메시지를 먼저 받아도 DDB 상태가 RECEIVED로 남아 defer 무한루프에 빠지지 않게 함.
        // 실패 처리에서 어떤 상태에서 FAILED_FINAL로 내릴지 기억
        RequestStatus statusForFailureTransition = RequestStatus.RECEIVED;

        // 최초 요청일 때만 QUEUED 전이(및 SQS enqueue)를 수행
        long queuedAt = System.currentTimeMillis();

        try {
            if (!isDuplicate) {
                Map<String, Object> patch = new HashMap<>();
                patch.put("queuedAt", queuedAt);
                patch.put("GSI1PK", DdbKeyFactory.userPk(userId));
                patch.put("GSI1SK", DdbKeyFactory.userRequestSk(queuedAt, requestId));
                patch.put("GSI2PK", DdbKeyFactory.eventPk(eventId));
                patch.put("GSI2SK", DdbKeyFactory.eventRequestSk(queuedAt, requestId));

                // ✅ [문제 5] 전이 결과 검증: TransitionResult 기반으로 성공 여부 확인
                // - TransitionResult는 boolean이 아니고, Success/ConditionFailed/UnexpectedError 등으로 내려옴
                TransitionResult transitionResult = stateTransitionService.transition(
                        requestId,
                        RequestStatus.RECEIVED,
                        RequestStatus.QUEUED,
                        patch
                );

                // SUCCESS가 아니면 SQS enqueue 금지
                if (!(transitionResult instanceof TransitionResult.Success)) {
                    // 전이 실패면 지금 케이스는 "SQS를 보내면 안 되는" 케이스
                    throw new IllegalStateException("RECEIVED -> QUEUED transition failed: " + transitionResult);
                }

                statusForFailureTransition = RequestStatus.QUEUED;

                // ✅ [문제 3] 중복 요청은 SQS enqueue 금지 (최초 요청일 때만 보냄)
                ParticipationMessage msg = new ParticipationMessage(
                        requestId, eventId, userId, queuedAt, EventType.FIRST_COME
                );

                String body = objectMapper.writeValueAsString(msg);

                Map<String, MessageAttributeValue> attributes = Map.of(
                        "requestId", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(requestId)
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
            // ✅ [문제 4] 전이를 QUEUED까지 해둔 뒤 실패했을 수 있으므로,
            //            현재 위치(statusForFailureTransition)에서 FAILED_FINAL로 내림
            Map<String, Object> patch = new HashMap<>();
            patch.put("resultCode", ResultCode.FAILED_INGEST_ENQUEUE);
            patch.put("uiResult", UiResult.FAILED);
            patch.put("finishedAt", nowFail);
            patch.put("errorMessage", "SQS enqueue failed: " + e.getMessage());

            stateTransitionService.transition(
                    requestId,
                    statusForFailureTransition,
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
