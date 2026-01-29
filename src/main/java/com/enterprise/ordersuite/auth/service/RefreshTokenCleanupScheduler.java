package com.enterprise.ordersuite.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenCleanupService cleanupService;

    // every day at 03:15
    @Scheduled(cron = "0 15 3 * * *")
    public void cleanupRefreshTokens() {
        cleanupService.cleanupNow();
    }
}
