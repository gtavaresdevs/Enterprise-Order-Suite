package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
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
            String restaurantId,
            String actorUserId,
            String targetUserId,
            Map<String, Object> details
    ) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                type,
                restaurantId,
                actorUserId,
                targetUserId,
                details
        );

        return repository.save(event);
    }
}
