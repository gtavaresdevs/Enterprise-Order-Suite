package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.common.tenancy.TenantUnscoped;
import com.enterprise.ordersuite.identity.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    @TenantUnscoped("Resolves the signed-in user's restaurant (D-6): it is how the tenant is found.")
    Optional<Membership> findByUserId(String userId);
}
