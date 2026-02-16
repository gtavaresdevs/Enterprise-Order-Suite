package com.enterprise.ordersuite.identity.application.mapper;

import com.enterprise.ordersuite.identity.api.dto.IdentityAuditEventResponse;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuditMapper {

    public IdentityAuditEventResponse toResponse(IdentityAuditEvent e) {
        return new IdentityAuditEventResponse(
                e.getId(),
                e.getType(),
                e.getActorUserId(),
                e.getTargetUserId(),
                e.getMetadata(),
                e.getCreatedAt()
        );
    }
}
