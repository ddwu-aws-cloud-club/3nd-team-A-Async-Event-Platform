package com.teamA.async.common.ddb.keys;

/**
 * DynamoDB PK/SK 문자열 규칙을 단일 진실로 관리한다.
 *
 * ❗주의
 * - 절대 다른 곳에서 문자열을 직접 조합하지 말 것
 * - 모든 PK/SK는 이 Factory를 통해 생성한다
 */
public final class DdbKeyFactory {

    private static final String REQ_PREFIX = "REQ#";
    private static final String IDEMP_PREFIX = "IDEMP#";
    private static final String EVENT_PREFIX = "EVENT#";

    private DdbKeyFactory() {
        // util class
    }

    /** RequestItem PK: REQ#<requestId> */
    public static String requestPk(String requestId) {
        return REQ_PREFIX + requestId;
    }

    /** IdempotencyLock PK: IDEMP#<eventId>#<userId> */
    public static String idempotencyPk(String eventId, String userId) {
        return IDEMP_PREFIX + eventId + "#" + userId;
    }

    /** Event/Capacity PK: EVENT#<eventId> */
    public static String eventPk(String eventId) {
        return EVENT_PREFIX + eventId;
    }
}
