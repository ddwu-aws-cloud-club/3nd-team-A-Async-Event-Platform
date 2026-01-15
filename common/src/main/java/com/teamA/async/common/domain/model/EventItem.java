package com.teamA.async.common.domain.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class EventItem {
    private String pk;
    private String sk;

    // 프론트엔드 노출용 필드
    @Getter(onMethod_ = @DynamoDbAttribute("eventId"))
    private String eventId;
    @Getter(onMethod_ = @DynamoDbAttribute("eventType"))
    private String eventType;
    @Getter(onMethod_ = @DynamoDbAttribute("title"))
    private String title;
    @Getter(onMethod_ = @DynamoDbAttribute("status"))
    private String status;
    // deadline을 만들기 위한 원천 데이터
    private Integer capacityTotal;
    private Integer lotteryCutoffAt;

    private Integer capacityRemaining; // 현재 남은 좌석 (실시간 변동)

    // LOTTERY 전용 필드
    private Long lotteryDrawnAt; // null이면 아직 추첨 안 됨

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }
}
