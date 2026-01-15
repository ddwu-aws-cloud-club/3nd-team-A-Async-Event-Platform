package com.teamA.async.ingest.config;

import com.teamA.async.common.transition.DynamoStateTransitionService;
import com.teamA.async.common.transition.StateTransitionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class StateTransitionConfig {

    @Bean
    public StateTransitionService stateTransitionService(
            DynamoDbClient dynamoDbClient,
            @Value("${ddb.table-name}") String tableName
    ) {
        return new DynamoStateTransitionService(dynamoDbClient, tableName);
    }
}

