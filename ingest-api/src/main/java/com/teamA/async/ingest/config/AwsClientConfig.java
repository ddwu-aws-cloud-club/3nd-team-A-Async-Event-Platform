package com.teamA.async.ingest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

@Configuration
public class AwsClientConfig {
    //AwsClientConfig.java에서 SqsClient 생성 방식을 바꾼
    //이전에는 커넥션 풀/타임아웃이 기본값이라 고RATE에서 connection pool 고갈이 발생
    @Bean
    public SqsClient sqsClient(@Value("${aws.region}") String region) {

        // ApacheHttpClient.builder().build()의 반환 타입은 ApacheHttpClient가 아니라 SdkHttpClient임
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .maxConnections(500)                                 //  풀 확장
                .connectionAcquisitionTimeout(Duration.ofSeconds(10)) //  풀 대기
                .connectionTimeout(Duration.ofSeconds(3))
                .socketTimeout(Duration.ofSeconds(5))
                .build();

        ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10))               //  전체 호출 상한
                .apiCallAttemptTimeout(Duration.ofSeconds(3))         //  1회 시도 상한
                .build();

        return SqsClient.builder()
                .region(Region.of(region))
                .httpClient(httpClient)
                .overrideConfiguration(override)
                .build();
    }
}