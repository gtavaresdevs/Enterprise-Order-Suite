package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// The one role a user acts as (Tenancy & Identity D-3): PLATFORM_ADMIN from the user flag,
// otherwise the membership role. Empty when the user has neither.
@Service
@RequiredArgsConstructor
public class UserRoleResolver {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public Optional<String> roleOf(User user) {
        if (user.isPlatformAdmin()) {
            return Optional.of(PLATFORM_ADMIN);
        }
        return membershipRepository.findByUserId(user.getId())
                .map(Membership::getRole)
                .map(Enum::name);
    }
}
