package com.teamA.async.worker.analytics.event;

//이 이벤트가 몇번째 시도로 처리되었는지/ dlq에서 온건지
public record ParticipationProcessedDelivery(
        int attempt, //몇 번쨰 수신/처리 시도인지
        boolean isDlq //이 이벤트가 DLQ에서 소비된 메시지의 최종 이벤트인지
) {
    public ParticipationProcessedDelivery {
        //attempt가 0이면 “시도 횟수” 개념이 무너지니 최소 1로 강제
        if (attempt <= 0) throw new IllegalArgumentException("attempt must be positive");
    }
}
