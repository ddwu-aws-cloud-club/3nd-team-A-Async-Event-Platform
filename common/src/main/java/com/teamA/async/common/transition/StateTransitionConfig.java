package com.teamA.async.common.transition;

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
