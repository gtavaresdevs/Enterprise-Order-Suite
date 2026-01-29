package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying
    @Transactional
    @Query("""
            delete from RefreshToken rt
            where rt.expiresAt < :now
            """)
    int deleteExpired(LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
            delete from RefreshToken rt
            where (rt.usedAt is not null and rt.usedAt < :cutoff)
               or (rt.revokedAt is not null and rt.revokedAt < :cutoff)
            """)
    int deleteUsedOrRevokedBefore(LocalDateTime cutoff);

}
