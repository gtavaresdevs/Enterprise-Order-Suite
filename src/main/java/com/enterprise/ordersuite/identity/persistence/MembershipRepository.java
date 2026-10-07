package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.identity.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    // Resolves the signed-in user's restaurant (D-6). It is how the tenant is found, so it
    // cannot take one.
    Optional<Membership> findByUserId(String userId);
}
