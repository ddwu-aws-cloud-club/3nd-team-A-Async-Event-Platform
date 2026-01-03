package com.teamA.async.worker.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkerHeartbeat {

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        log.info("[WORKER] alive");
    }
}
