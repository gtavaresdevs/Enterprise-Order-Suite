package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import com.enterprise.ordersuite.auth.service.tokens.RefreshTokenGenerator;
import com.enterprise.ordersuite.auth.service.tokens.TokenHashing;
import com.enterprise.ordersuite.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final Clock clock;

    private final Duration refreshTtl = Duration.ofDays(14);

    public IssuedRefreshToken issueFor(User user) {
        String raw = refreshTokenGenerator.generate();
        String hash = TokenHashing.sha256Hex(raw);

        LocalDateTime now = LocalDateTime.now(clock);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash);

        // BaseEntity handles createdAt
        token.setExpiresAt(now.plus(refreshTtl));

        refreshTokenRepository.save(token);

        return new IssuedRefreshToken(raw, token.getExpiresAt());
    }

    public RefreshToken getActiveTokenOrNull(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }

        String hash = TokenHashing.sha256Hex(rawRefreshToken);
        LocalDateTime now = LocalDateTime.now(clock);

        return refreshTokenRepository.findByTokenHash(hash)
                .filter(t -> t.isActive(now))
                .orElse(null);
    }

    public void markUsed(RefreshToken token) {
        token.setUsedAt(LocalDateTime.now(clock));
        refreshTokenRepository.save(token);
    }

    public void revoke(RefreshToken token) {
        token.setRevokedAt(LocalDateTime.now(clock));
        refreshTokenRepository.save(token);
    }

    // ---------- Helpers for refresh/logout flows ----------

    public String hash(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return null;
        }
        return TokenHashing.sha256Hex(rawRefreshToken);
    }

    public RefreshToken findByHashOrNull(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return refreshTokenRepository.findByTokenHash(hash).orElse(null);
    }

    public record IssuedRefreshToken(String rawToken, LocalDateTime expiresAt) {}
}
