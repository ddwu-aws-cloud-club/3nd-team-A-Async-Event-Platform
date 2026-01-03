package com.teamA.async.common.ddb.keys;

/**
 * DynamoDB PK/SK 문자열 규칙을 단일 진실로 관리한다.
 *
 * ❗주의
 * - 절대 다른 곳에서 문자열을 직접 조합하지 말 것
 * - 모든 PK/SK는 이 Factory를 통해 생성한다
 * - DDB는 키를 만드는 규칙만 가지고 실체 규칙을 이용해 값을 채워넣는건 RequestItem이 스스로 하게 함
 */
public final class DdbKeyFactory {

    // PK
    // USER#<userId>: GSI1PK
    private static final String REQ_PREFIX = "REQ#";
    private static final String IDEMP_PREFIX = "IDEMP#";
    private static final String EVENT_PREFIX = "EVENT#";
    private static final String USER_PREFIX = "USER#";

    // SK
    private static final String META_SK = "META"; // RequestItem Base SK
    private static final String LOCK_SK = "LOCK"; // IdempotencyItem SK
    private static final String CAPACITY_SK = "CAPACITY"; // CapacityItem SK
    private static final String QAT_PREFIX = "QAT#"; // 줄 선 시간
    private static final String ST_PREFIX = "ST#"; // 상태별 필터링

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

    // GSI PK (유저 뷰): USER#<userId>
    public static String userPk(String userId) {
        return USER_PREFIX + userId;
    }

    // RequestItem SK: META
    public static String metaSk() { return META_SK; }

    // IdempotencyLock SK: LOCK
    public static String lockSk() { return LOCK_SK; }

    // GSI1 SK (내 신청 내역): QAT#<queuedAt>#REQ#<requestId>
    public static String userRequestSk(long queuedAt, String requestId) {
        return QAT_PREFIX + queuedAt + "#" + REQ_PREFIX + requestId;
    }

    // GSI2 SK (이벤트별 운영 조회): QAT#<queuedAt>#REQ#<requestId>
    /*
     * - 설계문서: queuedAt 기반 정렬 (상태 필터링이 필요 없다면 ST# 생략 가능하나,
     * - 문서 하단 "status로 DB 필터링은 안됨" 멘트를 고려하여 기본형으로 작성)
    */
    public static String eventRequestSk(long queuedAt, String requestId) {
        return QAT_PREFIX + queuedAt + "#" + REQ_PREFIX + requestId;
    }
}
