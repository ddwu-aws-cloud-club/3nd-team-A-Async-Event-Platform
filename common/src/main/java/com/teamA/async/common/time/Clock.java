package com.teamA.async.common.time;

/**
 * 시간 획득을 추상화하기 위한 인터페이스.
 * - 테스트에서 시간 고정을 쉽게 하기 위함
 */
public interface Clock {

    /** 현재 시각을 epoch milliseconds로 반환 */
    long nowMillis();
}

