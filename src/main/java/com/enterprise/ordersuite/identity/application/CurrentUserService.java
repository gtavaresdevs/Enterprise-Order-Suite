package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public String getUserId() {
        Authentication auth = authenticationOrThrow();
        if (auth.getPrincipal() instanceof JwtUserPrincipal principal) {
            return principal.id();
        }
        throw new IllegalStateException("Authenticated principal is not an access-token user");
    }

    @Transactional(readOnly = true)
    public User requireUser() {
        String userId = getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    @Transactional(readOnly = true)
    public User requireActiveUser() {
        User user = requireUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("User account is inactive");
        }
        return user;
    }

    private Authentication authenticationOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated user");
        }
        return auth;
    }
}
