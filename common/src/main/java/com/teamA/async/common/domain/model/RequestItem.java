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
    // PK: EVENT#{eventId} / SK: QAT#{queuedAt}#ST#{status}#REQ#{requestId}  // status 포함 금지
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
    @Getter(onMethod_ = {@DynamoDbAttribute("requestId")})
    private String requestId;

    @Getter(onMethod_ = {@DynamoDbAttribute("eventId")})
    private String eventId;

    @Getter(onMethod_ = {@DynamoDbAttribute("userId")})
    private String userId;

    @Getter(onMethod_ = {@DynamoDbAttribute("eventType")})
    private EventType eventType;

    @Getter(onMethod_ = {@DynamoDbAttribute("status")})
    private RequestStatus status;

    // 상태 및 결과 정보
    // Enum들은 기본적으로 name() (문자열)으로 저장됨
    @Getter(onMethod_ = {@DynamoDbAttribute("uiResult")})
    private UiResult uiResult;

    @Getter(onMethod_ = {@DynamoDbAttribute("resultCode")})
    private ResultCode resultCode;

    // 타임스탬프
    @Getter(onMethod_ = {@DynamoDbAttribute("requestedAt")})
    private Long requestedAt;

    // SQS Enqueue 시점
    @Getter(onMethod_ = {@DynamoDbAttribute("queuedAt")})
    private Long queuedAt;

    // Worker 처리 시작
    @Getter(onMethod_ = {@DynamoDbAttribute("startedAt")})
    private Long startedAt;

    // 처리 완료
    @Getter(onMethod_ = {@DynamoDbAttribute("finishedAt")})
    private Long finishedAt;

    // 실패 상세 정보
    private FailureClass failureClass;
    private String errorCode;
    private String errorMessage;

    // 참조용 키 (Idempotency)
    private String idempotencyKey; // IDEMP#{eventId}#{userId}

    // TTL (Time To Live) - 자동 삭제 시간
    private Long ttl;

    // DdbKeyFactory 연결, DB 저장 전 호출해야 함!
    public void generateBaseKeys() {
        if (requestId == null || eventId == null || userId == null) {
            throw new IllegalStateException("Base 키 생성 오류: 필수 필드 누락");
        }
        // 기본 테이블 키만 생성
        this.pk = DdbKeyFactory.requestPk(requestId);
        this.sk = DdbKeyFactory.metaSk();
        // GSI 키는 건드리지 않음 (null 상태 유지)
    }

    // 2. GSI 키 생성 메서드 추가 (QUEUED 전이 시 호출)
    public void generateGsiKeys() {
        if (queuedAt == null) {
            throw new IllegalStateException("GSI 키 생성 오류: queuedAt 필수");
        }
        // GSI1 (내 신청 내역)
        this.gsi1Pk = DdbKeyFactory.userPk(userId);
        this.gsi1Sk = DdbKeyFactory.userRequestSk(queuedAt, requestId);

        // GSI2 (이벤트/관리자 조회)
        this.gsi2Pk = DdbKeyFactory.eventPk(eventId);
        this.gsi2Sk = DdbKeyFactory.eventRequestSk(queuedAt, requestId);
    }
}