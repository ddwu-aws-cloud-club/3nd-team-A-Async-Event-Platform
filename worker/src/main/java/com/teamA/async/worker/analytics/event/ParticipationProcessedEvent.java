package com.teamA.async.worker.analytics.event;

import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;

//이벤트 한 건에 대한-Athena에서 한이벤트를 한줄로 분석할때, 필드가 누락되거나 구조가 뒤틀리지않도록 스키마를 코드로 고정
public record ParticipationProcessedEvent(
        int schemaVersion,
        String env,//이 이벤트가 어디 환경에서 발생했는지(ex. dev/prod)

        String eventId,
        String requestId,
        String userId,

        EventType eventType,
        RequestStatus finalStatus, //SUCCEEDED/REJECTED/FAILED_FINAL
        ResultCode resultCode, //SUCCESS/REJECTED_CAPACITY

        ParticipationProcessedTimestamps timestamps, //queuedAt을 기준으로 대기/전체 지연을 계산
        ParticipationProcessedDelivery delivery, //메시지가 몇 번째 시도로 처리됐는지 / DLQ 메시지
        ParticipationProcessedFailure failure, //실패 관련 추가 정보-실패가 아닌경우 null일 수 있음
        ParticipationProcessedMeta meta
) {
    //record 생성 시 검증
    public ParticipationProcessedEvent {
        // Step2 목적: "필드 누락 방지" -> 생성 시점에 강제 검증
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (env == null || env.isBlank()) throw new IllegalArgumentException("env is required");

        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");

        if (eventType == null) throw new IllegalArgumentException("eventType is required");
        if (finalStatus == null) throw new IllegalArgumentException("finalStatus is required");
        if (resultCode == null) throw new IllegalArgumentException("resultCode is required");

        //timestamps/delivery/failure/meta는 “중첩 객체 구조”를 고정하기 위한 필드
        if (timestamps == null) throw new IllegalArgumentException("timestamps is required");
        if (delivery == null) throw new IllegalArgumentException("delivery is required");
        if (failure == null) throw new IllegalArgumentException("failure is required"); // ⭐ 문서 스키마 고정용
        if (meta == null) throw new IllegalArgumentException("meta is required");
    }

    /**
     * 성공/거절 등 "실패 상세 없음" 케이스에서도 failure 객체는 유지하고 내부만 null로 둔다.
     * (ObjectMapper가 null 제거 안 하므로)
     */
    public static ParticipationProcessedEvent noFailure(
            int schemaVersion,
            String env,
            String eventId,
            String requestId,
            String userId,
            EventType eventType,
            RequestStatus finalStatus,
            ResultCode resultCode,
            ParticipationProcessedTimestamps timestamps,
            ParticipationProcessedDelivery delivery,
            ParticipationProcessedMeta meta
    ) {
        return new ParticipationProcessedEvent(
                schemaVersion, env,
                eventId, requestId, userId,
                eventType, finalStatus, resultCode,
                timestamps, delivery,
                new ParticipationProcessedFailure(null, null, null),
                meta
        );
    }
}
