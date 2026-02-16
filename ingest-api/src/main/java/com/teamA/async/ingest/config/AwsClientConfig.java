package com.teamA.async.ingest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsClientConfig {
    @Bean
    public SqsClient sqsClient(@Value("${aws.region}") String region) {
        return SqsClient.builder()
                .region(Region.of(region))
                .build(); // ✅ ECS에서는 Task Role로 자동 인증
    }
}
