package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    // keep used/revoked tokens for 7 days (for troubleshooting/audit)
    private final Duration usedRevokedRetention = Duration.ofDays(7);

    public CleanupResult cleanupNow() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(usedRevokedRetention);

        int expiredDeleted = refreshTokenRepository.deleteExpired(now);
        int usedRevokedDeleted = refreshTokenRepository.deleteUsedOrRevokedBefore(cutoff);

        return new CleanupResult(expiredDeleted, usedRevokedDeleted);
    }

    public record CleanupResult(int expiredDeleted, int usedRevokedDeleted) {}
}
