package com.enterprise.ordersuite.auth.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import com.enterprise.ordersuite.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isActive(LocalDateTime now) {
        return !isExpired(now) && !isRevoked() && !isUsed();
    }
}
