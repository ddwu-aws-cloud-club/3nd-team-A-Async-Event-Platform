package com.teamA.async.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class AwsClientConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.profile}")
    private String profile;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        ProfileCredentialsProvider.create(profile)
                )
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        ProfileCredentialsProvider.create(profile)
                )
                .build();
    }
}