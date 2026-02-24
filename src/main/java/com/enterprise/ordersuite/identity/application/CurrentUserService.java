package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getEmail() {
        return authenticationOrThrow().getName();
    }

    @Transactional(readOnly = true)
    public User requireUser() {
        String email = getEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    @Transactional(readOnly = true)
    public User requireActiveUser() {
        User user = requireUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("User is inactive");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Long getUserId() {
        return requireUser().getId();
    }

    @Transactional(readOnly = true)
    public Long getActiveUserId() {
        return requireActiveUser().getId();
    }

    private Authentication authenticationOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated user");
        }
        return auth;
    }
}
