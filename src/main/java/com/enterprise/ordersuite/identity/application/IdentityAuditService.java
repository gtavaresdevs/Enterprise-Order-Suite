package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuditService {

    private final IdentityAuditEventRepository repository;

    public IdentityAuditService(IdentityAuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IdentityAuditEvent recordEvent(
            IdentityAuditEventType type,
            Long actorUserId,
            Long targetUserId,
            String metadata
    ) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                type,
                actorUserId,
                targetUserId,
                metadata
        );

        return repository.save(event);
    }
}
