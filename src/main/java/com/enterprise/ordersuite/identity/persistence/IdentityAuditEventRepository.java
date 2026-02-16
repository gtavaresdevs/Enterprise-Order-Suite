package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAuditEventRepository extends JpaRepository<IdentityAuditEvent, Long> {

    Page<IdentityAuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<IdentityAuditEvent> findByTypeOrderByCreatedAtDesc(
            IdentityAuditEventType type,
            Pageable pageable
    );

    Page<IdentityAuditEvent> findByTargetUserIdOrderByCreatedAtDesc(
            Long targetUserId,
            Pageable pageable
    );

    Page<IdentityAuditEvent> findByActorUserIdOrderByCreatedAtDesc(
            Long actorUserId,
            Pageable pageable
    );
}
