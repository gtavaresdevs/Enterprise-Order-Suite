package com.enterprise.ordersuite.identity.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Entity
@Table(name = "identity_audit_events")
public class IdentityAuditEvent extends BaseEntity {

    // Null only for platform-level events (restaurant created).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "restaurant_id", length = 26)
    private String restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private IdentityAuditEventType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_user_id", length = 26)
    private String actorUserId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "target_user_id", length = 26)
    private String targetUserId;

    // Type-specific; never a secret or a token (for ROLE_CHANGED: { from, to }).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    protected IdentityAuditEvent() {
    }

    public IdentityAuditEvent(
            IdentityAuditEventType type,
            String restaurantId,
            String actorUserId,
            String targetUserId,
            Map<String, Object> details
    ) {
        this.type = type;
        this.restaurantId = restaurantId;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.details = details == null ? Map.of() : details;
    }

}
