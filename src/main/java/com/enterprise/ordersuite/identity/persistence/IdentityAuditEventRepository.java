package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

// Audit events are restaurant-owned: any finder added here takes the restaurant id
// (TenantRepositoryRuleTest).
public interface IdentityAuditEventRepository extends JpaRepository<IdentityAuditEvent, String> {
}
