package com.teamA.async.common.transition;

import com.teamA.async.common.domain.enums.RequestStatus;

import java.util.Map;

/**
 * G0 공통 상태 전이 서비스
 *
 * ❗규칙
 * - DynamoDB UpdateItem은 반드시 이 계층을 통해서만 수행
 * - Condition(status = from)은 항상 적용
 */
public interface StateTransitionService {

    TransitionResult transition(
            String requestId,
            RequestStatus from,
            RequestStatus to,
            Map<String, Object> patchFields
    );
}
