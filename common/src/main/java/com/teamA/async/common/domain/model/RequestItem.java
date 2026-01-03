package com.teamA.async.common.domain.model;

import com.teamA.async.common.ddb.keys.DdbKeyFactory;
import com.teamA.async.common.domain.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestItem {

    // 1. pk, sk
    private String pk;
    private String sk;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() { return pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() { return sk; }

    // 2. Global Secondary Indexes (GSI)
    // 이 필드들은 RequestItem에만 존재하며, 다른 Item(Capacity 등)은 null이므로 인덱스 비용 절감

    // GSI1: 유저별 신청 내역 (PK: USER#{userId} / SK: QAT#{queuedAt}#REQ#{requestId})
    private String gsi1Pk;
    private String gsi1Sk;

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1PK")
    public String getGsi1Pk() { return gsi1Pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1SK")
    public String getGsi1Sk() { return gsi1Sk; }

    // GSI2: 이벤트별/관리자 조회
    // PK: EVENT#{eventId} / SK: QAT#{queuedAt}#ST#{status}#REQ#{requestId}
    private String gsi2Pk;
    private String gsi2Sk;

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI2")
    @DynamoDbAttribute("GSI2PK")
    public String getGsi2Pk() { return gsi2Pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI2")
    @DynamoDbAttribute("GSI2SK")
    public String getGsi2Sk() { return gsi2Sk; }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return this.ttl;
    }

    // Domain Attributes
    // 식별자 정보
    private String requestId;
    private String eventId;
    private String userId;

    // 상태 및 결과 정보
    // Enum들은 기본적으로 name() (문자열)으로 저장됨
    private EventType eventType;
    private RequestStatus status;
    private UiResult uiResult;
    private ResultCode resultCode;

    // 타임스탬프
    private Long requestedAt; // 202 응답 시점
    private Long queuedAt;    // SQS Enqueue 시점
    private Long startedAt;   // Worker 처리 시작
    private Long finishedAt;  // 처리 완료

    // 실패 상세 정보
    private FailureClass failureClass;
    private String errorCode;
    private String errorMessage;

    // 참조용 키 (Idempotency)
    private String idempotencyKey; // IDEMP#{eventId}#{userId}

    // TTL (Time To Live) - 자동 삭제 시간
    private Long ttl;

    // DdbKeyFactory 연결, DB 저장 전 호출해야 함!
    public void generateKeys() {
        if (requestId == null || eventId == null || userId == null) {
            throw new IllegalStateException("키 생성 오류: requestId, eventId, userId must not be null");
        }

        // Base Table Keys
        this.pk = DdbKeyFactory.requestPk(requestId);
        this.sk = DdbKeyFactory.metaSk();

        // GSI1 Keys (User View)
        this.gsi1Pk = DdbKeyFactory.userPk(userId);
        // queuedAt이 없으면 0으로 처리 (혹은 requestedAt 사용 정책에 따라 변경 가능)
        long qTime = (queuedAt != null) ? queuedAt : 0L;
        this.gsi1Sk = DdbKeyFactory.userRequestSk(qTime, requestId);

        // GSI2 Keys (Event/Admin View)
        this.gsi2Pk = DdbKeyFactory.eventPk(eventId);
        this.gsi2Sk = DdbKeyFactory.eventRequestSk(qTime, requestId);
    }
}