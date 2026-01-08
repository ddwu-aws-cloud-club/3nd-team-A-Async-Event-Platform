package com.teamA.async.worker.analytics.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamA.async.common.domain.enums.EventType;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipationProcessedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeParticipationProcessedEventWithExactSchema() throws Exception {
        ParticipationProcessedEvent event =
                ParticipationProcessedEvent.noFailure(
                        1,
                        "dev",
                        "EVT-001",
                        "REQ-123",
                        "user-1",
                        EventType.FIRST_COME,
                        RequestStatus.SUCCEEDED,
                        ResultCode.SUCCESS,
                        new ParticipationProcessedTimestamps(
                                1704290000000L,
                                1704290000100L,
                                1704290000200L
                        ),
                        new ParticipationProcessedDelivery(1, false),
                        new ParticipationProcessedMeta("worker-local", false)
                );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(json).contains("\"env\":\"dev\"");
        assertThat(json).contains("\"eventId\":\"EVT-001\"");
        assertThat(json).contains("\"requestId\":\"REQ-123\"");
        assertThat(json).contains("\"userId\":\"user-1\"");
        assertThat(json).contains("\"eventType\":\"FIRST_COME\"");
        assertThat(json).contains("\"finalStatus\":\"SUCCEEDED\"");
        assertThat(json).contains("\"resultCode\":\"SUCCESS\"");

        assertThat(json).contains("\"timestamps\"");
        assertThat(json).contains("\"queuedAt\":1704290000000");
        assertThat(json).contains("\"startedAt\":1704290000100");
        assertThat(json).contains("\"finishedAt\":1704290000200");

        assertThat(json).contains("\"delivery\"");
        assertThat(json).contains("\"attempt\":1");
        assertThat(json).contains("\"isDlq\":false");

        assertThat(json).contains("\"failure\"");
        assertThat(json).contains("\"meta\"");
        assertThat(json).contains("\"workerId\":\"worker-local\"");
    }
}
