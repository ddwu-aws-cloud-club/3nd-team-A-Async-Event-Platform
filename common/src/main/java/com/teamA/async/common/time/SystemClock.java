package com.teamA.async.common.time;

/**
 * 실제 시스템 시간을 사용하는 기본 구현체
 */
public class SystemClock implements Clock {

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}