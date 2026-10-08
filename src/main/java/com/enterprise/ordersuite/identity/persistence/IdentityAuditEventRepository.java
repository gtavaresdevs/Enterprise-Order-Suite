package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Audit events are restaurant-owned: any finder added here takes the restaurant id
// (TenantRepositoryRuleTest).
public interface IdentityAuditEventRepository extends JpaRepository<IdentityAuditEvent, String> {

    // GET /audit-events: newest first, then id (the draft's x-default-order).
    @Query("select e from IdentityAuditEvent e where e.restaurantId = :restaurantId"
            + " and (:type is null or e.type = :type) order by e.createdAt desc, e.id desc")
    Page<IdentityAuditEvent> findForRestaurant(
            @Param("restaurantId") String restaurantId,
            @Param("type") IdentityAuditEventType type,
            Pageable pageable
    );
}
