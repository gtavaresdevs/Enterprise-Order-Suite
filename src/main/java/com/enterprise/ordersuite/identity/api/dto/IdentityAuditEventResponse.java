package com.enterprise.ordersuite.identity.api.dto;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;

import java.time.LocalDateTime;

public record IdentityAuditEventResponse(
        Long id,
        IdentityAuditEventType type,
        Long actorUserId,
        Long targetUserId,
        String metadata,
        LocalDateTime createdAt
) {}
