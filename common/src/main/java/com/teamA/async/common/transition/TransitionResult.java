package com.teamA.async.common.transition;

/**
 * 상태 전이 결과
 *
 * - Success: 정상 전이
 * - ConditionFailed: status 불일치 (경쟁/중복 상황)
 */
public sealed interface TransitionResult
        permits TransitionResult.Success, TransitionResult.ConditionFailed {

    record Success() implements TransitionResult {}

    record ConditionFailed() implements TransitionResult {}
}
