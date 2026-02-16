package com.enterprise.ordersuite.identity.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "identity_audit_events")
public class IdentityAuditEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private IdentityAuditEventType type;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    protected IdentityAuditEvent() {
    }

    public IdentityAuditEvent(
            IdentityAuditEventType type,
            Long actorUserId,
            Long targetUserId,
            String metadata
    ) {
        this.type = type;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.metadata = metadata;
    }

}
