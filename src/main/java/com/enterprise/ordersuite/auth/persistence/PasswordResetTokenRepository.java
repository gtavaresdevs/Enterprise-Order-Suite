package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.PasswordResetToken;
import com.enterprise.ordersuite.auth.domain.PasswordResetTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Marks the user's unused tokens of one purpose as used, so a resent invite kills the old link.
    @Modifying
    @Query("""
            update PasswordResetToken t
            set t.usedAt = :now
            where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidateUnused(String userId, PasswordResetTokenPurpose purpose, Instant now);
}
