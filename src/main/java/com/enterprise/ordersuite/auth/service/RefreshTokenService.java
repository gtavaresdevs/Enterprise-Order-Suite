package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import com.enterprise.ordersuite.auth.service.tokens.RefreshTokenGenerator;
import com.enterprise.ordersuite.auth.service.tokens.TokenHashing;
import com.enterprise.ordersuite.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public static final Duration REFRESH_TTL = Duration.ofDays(30);

    public IssuedRefreshToken issueFor(User user) {
        return issue(user, UUID.randomUUID());
    }

    public IssuedRefreshToken rotate(RefreshToken current) {
        current.setUsedAt(Instant.now(clock));
        refreshTokenRepository.save(current);
        return issue(current.getUser(), current.getFamilyId());
    }

    // Returns the token in any state - used, revoked or expired - so the caller can tell
    // reuse apart from an unknown token. Must run inside a transaction (row lock). Used by
    // logout too: the lock waits for a concurrent rotation of this token to commit, so the
    // family revocation that follows also sees the successor that rotation inserted.
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

    // Lock first, then revoke in a separate statement. Under READ COMMITTED the bulk update
    // alone would miss a successor that a concurrent rotation inserted but had not committed;
    // the lock waits for that commit and the update's fresh snapshot then includes it.
    @Transactional
    public void revokeAllFor(User user) {
        refreshTokenRepository.findUnrevokedByUserIdForUpdate(user.getId());
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

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}
}
