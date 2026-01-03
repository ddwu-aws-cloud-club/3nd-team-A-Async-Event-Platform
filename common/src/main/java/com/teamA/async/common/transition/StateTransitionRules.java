package com.teamA.async.common.transition;

import com.teamA.async.common.domain.enums.RequestStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * G0 고정 상태머신의 "허용 전이"만 정의한다.
 * - UpdateItem 수행 로직은 여기 넣지 않는다. (Step0-A 범위 아님)
 */
public final class StateTransitionRules {

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = new EnumMap<>(RequestStatus.class);

    static {
        // RECEIVED -> QUEUED
        ALLOWED.put(RequestStatus.RECEIVED, EnumSet.of(RequestStatus.QUEUED));

        // QUEUED -> PROCESSING
        ALLOWED.put(RequestStatus.QUEUED, EnumSet.of(RequestStatus.PROCESSING));

        // PROCESSING -> (SUCCEEDED | REJECTED | FAILED_FINAL)
        ALLOWED.put(
                RequestStatus.PROCESSING,
                EnumSet.of(RequestStatus.SUCCEEDED, RequestStatus.REJECTED, RequestStatus.FAILED_FINAL)
        );

        // 최종 상태는 추가 전이 없음
        ALLOWED.put(RequestStatus.SUCCEEDED, EnumSet.noneOf(RequestStatus.class));
        ALLOWED.put(RequestStatus.REJECTED, EnumSet.noneOf(RequestStatus.class));
        ALLOWED.put(RequestStatus.FAILED_FINAL, EnumSet.noneOf(RequestStatus.class));
    }

    private StateTransitionRules() {}

    /** from -> to 전이가 허용되는지 */
    public static boolean isAllowed(RequestStatus from, RequestStatus to) {
        if (from == null || to == null) return false;
        Set<RequestStatus> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }

    /** 현재 상태에서 갈 수 있는 다음 상태들(읽기 전용) */
    public static Set<RequestStatus> nextStatuses(RequestStatus from) {
        if (from == null) return Set.of();
        Set<RequestStatus> next = ALLOWED.get(from);
        return next == null ? Set.of() : Set.copyOf(next);
    }
}
