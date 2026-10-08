package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.common.tenancy.TenantUnscoped;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    @TenantUnscoped("Resolves the signed-in user's restaurant (D-6): it is how the tenant is found.")
    Optional<Membership> findByUserId(String userId);

    // {memberId} is the user id; a member of another restaurant is not found (§5.6.1).
    @Query("select m from Membership m join fetch m.user u where m.restaurantId = :restaurantId and u.id = :userId")
    Optional<Membership> findMember(@Param("restaurantId") String restaurantId, @Param("userId") String userId);

    // The order comes from the Pageable (TeamService builds it, ending with the id).
    @Query(value = """
            select m from Membership m join fetch m.user u
            where m.restaurantId = :restaurantId
              and (:role is null or m.role = :role)
              and (:active is null or u.active = :active)
            """,
            countQuery = """
            select count(m) from Membership m join m.user u
            where m.restaurantId = :restaurantId
              and (:role is null or m.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<Membership> findMembers(
            @Param("restaurantId") String restaurantId,
            @Param("role") MembershipRole role,
            @Param("active") Boolean active,
            Pageable pageable
    );

    // Serializes changes that could remove the last owner (D-16): two owners demoting each
    // other at once both wait here, and the second then counts after the first committed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.restaurantId = :restaurantId"
            + " and m.role = com.enterprise.ordersuite.identity.domain.MembershipRole.OWNER")
    List<Membership> lockOwners(@Param("restaurantId") String restaurantId);

    // Run after lockOwners, as its own statement, so it reads what the other transaction committed.
    @Query("select count(m) from Membership m join m.user u where m.restaurantId = :restaurantId"
            + " and m.role = com.enterprise.ordersuite.identity.domain.MembershipRole.OWNER and u.active = true")
    long countActiveOwners(@Param("restaurantId") String restaurantId);
}
