package com.teamA.async.worker.analytics.event;

import com.teamA.async.common.domain.enums.FailureClass;
//실패일때-실패 분류/에러 정보
//성공/거절 같은 정상 흐름이면 이 안의 값들이 null 일 수 있음
public record ParticipationProcessedFailure(
        FailureClass failureClass, //실패가 재시도 가능한지 아닌지
        String errorCode, //내부적으로 규정한 에러 코드(예: FAILED_XXX)
        String errorMessage //디버깅용 메시지(운영에서 원인 파악 용도)
) {}
