package com.teamA.async.ingest.smoke.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsSmokeConfig {

    @Bean
    DynamoDbClient dynamoDbClient(
            @Value("${aws.region}") String region,
            @Value("${aws.profile:wish}") String profile //여기 iam 롤에 맞춰 변경!
    ) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(ProfileCredentialsProvider.create(profile))
                .build();
    }

    @Bean
    SqsClient sqsClient(
            @Value("${aws.region}") String region,
            @Value("${aws.profile:wish}") String profile//여기 iam 롤에 맞춰 변경!
    ) {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(ProfileCredentialsProvider.create(profile))
                .build();
    }
}
