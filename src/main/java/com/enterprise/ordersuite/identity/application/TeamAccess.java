package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.common.tenancy.TenantContext;
import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The team half of the §5.3 matrix, referenced from @PreAuthorize as @teamAccess on every team
 * write. An owner acts on anyone and grants any role; a manager acts only on staff and grants
 * only STAFF; anyone else is refused.
 *
 * The actor is judged by their membership as it is now, not by the token's role: a member
 * deactivated or demoted in the last 15 minutes still holds a valid token (D-11), and it must
 * not keep making team changes (Gabriel, Build 1 slice 6). The role is expanded through
 * RoleHierarchy, never compared raw.
 *
 * A target outside the caller's restaurant is allowed here, so the service answers 404
 * MEMBER_NOT_FOUND rather than a 403 that would confirm the member exists (§5.6.1).
 */
@Component("teamAccess")
@RequiredArgsConstructor
public class TeamAccess {

    private static final String OWNER = "ROLE_OWNER";
    private static final String MANAGER = "ROLE_MANAGER";

    private final MembershipRepository membershipRepository;
    private final RoleHierarchy roleHierarchy;

    @Transactional(readOnly = true)
    public boolean canInvite(MembershipRole role) {
        return actorAuthorities().map(actor -> mayGrant(actor, role)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canActOn(String memberId) {
        return actorAuthorities().map(actor -> mayActOn(actor, memberId)).orElse(false);
    }

    // PATCH: the target rule, plus the grant rule when a role is sent.
    @Transactional(readOnly = true)
    public boolean canUpdate(String memberId, MembershipRole newRole) {
        return actorAuthorities()
                .map(actor -> mayActOn(actor, memberId) && (newRole == null || mayGrant(actor, newRole)))
                .orElse(false);
    }

    private boolean mayGrant(Set<String> actor, MembershipRole role) {
        if (actor.contains(OWNER)) {
            return true;
        }
        return actor.contains(MANAGER) && role == MembershipRole.STAFF;
    }

    private boolean mayActOn(Set<String> actor, String memberId) {
        if (actor.contains(OWNER)) {
            return true;
        }
        if (!actor.contains(MANAGER)) {
            return false;
        }
        return membershipRepository.findMember(TenantContextHolder.requireRestaurantId(), memberId)
                .map(target -> target.getRole() == MembershipRole.STAFF)
                .orElse(true);
    }

    // Empty when the actor has no active membership in the context's restaurant. Support
    // reads never reach a write (TenantContextFilter refuses the header off GET).
    private Optional<Set<String>> actorAuthorities() {
        Optional<TenantContext> context = TenantContextHolder.get();
        if (context.isEmpty() || context.get().restaurantId() == null || context.get().support()) {
            return Optional.empty();
        }
        return membershipRepository.findMember(context.get().restaurantId(), context.get().userId())
                .filter(membership -> Boolean.TRUE.equals(membership.getUser().getActive()))
                .map(Membership::getRole)
                .map(this::reachable);
    }

    private Set<String> reachable(MembershipRole role) {
        return roleHierarchy.getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
