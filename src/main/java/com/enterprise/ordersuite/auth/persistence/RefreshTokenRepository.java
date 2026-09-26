package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Serializes concurrent presentations of one token: the second waits, then sees used_at.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying
    @Transactional
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.familyId = :familyId and rt.revokedAt is null
            """)
    int revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Transactional
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.user.id = :userId and rt.revokedAt is null
            """)
    int revokeAllForUser(Long userId, Instant now);

    @Modifying
    @Transactional
    @Query("""
            delete from RefreshToken rt
            where rt.expiresAt < :now
            """)
    int deleteExpired(Instant now);

}
