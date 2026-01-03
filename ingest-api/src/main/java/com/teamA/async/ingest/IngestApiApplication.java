package com.teamA.async.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.teamA.async")
public class IngestApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestApiApplication.class, args);
    }
}
