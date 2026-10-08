package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    // Used and revoked tokens stay until their own expiry: a replayed token must still be
    // found to be recognised as reuse (D24). deleteExpired removes every token past expiry.
    public CleanupResult cleanupNow() {
        return new CleanupResult(refreshTokenRepository.deleteExpired(Instant.now(clock)));
    }

    public record CleanupResult(int expiredDeleted) {}
}
