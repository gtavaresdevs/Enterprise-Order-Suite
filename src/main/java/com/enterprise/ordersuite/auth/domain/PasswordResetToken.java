package com.enterprise.ordersuite.auth.domain;

import com.enterprise.ordersuite.common.persistence.UlidId;
import com.enterprise.ordersuite.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @UlidId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 26)
    private String id;

    // Many reset tokens can belong to one user
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // SHA-256 hex (64 chars)
    @Setter
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    // RESET or INVITE: invites reuse this table (Tenancy & Identity schema notes).
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PasswordResetTokenPurpose purpose = PasswordResetTokenPurpose.RESET;

    @Setter
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public PasswordResetToken() {}

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @Transient
    public boolean isUsed() {
        return usedAt != null;
    }
}
