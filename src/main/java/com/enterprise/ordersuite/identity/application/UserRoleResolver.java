package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// The one role a user acts as (Tenancy & Identity D-3): PLATFORM_ADMIN from the user flag,
// otherwise the membership role and its restaurant. Empty when the user has neither.
@Service
@RequiredArgsConstructor
public class UserRoleResolver {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final MembershipRepository membershipRepository;

    // restaurantId is null for the platform admin, who has no membership.
    public record ActingRole(String role, String restaurantId) {
    }

    @Transactional(readOnly = true)
    public Optional<ActingRole> resolve(User user) {
        if (user.isPlatformAdmin()) {
            return Optional.of(new ActingRole(PLATFORM_ADMIN, null));
        }
        return membershipRepository.findByUserId(user.getId())
                .map(membership -> new ActingRole(membership.getRole().name(), membership.getRestaurantId()));
    }

    @Transactional(readOnly = true)
    public Optional<String> roleOf(User user) {
        return resolve(user).map(ActingRole::role);
    }
}
