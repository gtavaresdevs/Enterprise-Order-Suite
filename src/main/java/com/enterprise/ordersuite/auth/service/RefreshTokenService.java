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
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final Clock clock;

    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    public IssuedRefreshToken issueFor(User user) {
        return issue(user, UUID.randomUUID());
    }

    public IssuedRefreshToken rotate(RefreshToken current) {
        current.setUsedAt(Instant.now(clock));
        refreshTokenRepository.save(current);
        return issue(current.getUser(), current.getFamilyId());
    }

    // Returns the token in any state - used, revoked or expired - so the caller can tell
    // reuse apart from an unknown token. Must run inside a transaction (row lock).
    public RefreshToken findForRotationOrNull(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        return refreshTokenRepository.findByTokenHashForUpdate(TokenHashing.sha256Hex(rawRefreshToken))
                .orElse(null);
    }

    public boolean isExpired(RefreshToken token) {
        return token.isExpired(Instant.now(clock));
    }

    public void revokeFamily(RefreshToken token) {
        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now(clock));
    }

    public void revokeAllFor(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        String raw = refreshTokenGenerator.generate();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHashing.sha256Hex(raw));
        token.setFamilyId(familyId);
        // BaseEntity handles createdAt
        token.setExpiresAt(Instant.now(clock).plus(REFRESH_TTL));

        refreshTokenRepository.save(token);

        return new IssuedRefreshToken(raw, token.getExpiresAt());
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

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}
}
