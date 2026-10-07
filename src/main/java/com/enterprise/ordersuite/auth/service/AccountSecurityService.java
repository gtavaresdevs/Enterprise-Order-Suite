package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.service.exceptions.InvalidCurrentPasswordException;
import com.enterprise.ordersuite.identity.application.CurrentUserService;
import com.enterprise.ordersuite.identity.application.IdentityAuditService;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

// The signed-in user's own credentials and sessions (Tenancy & Identity, Own account 2 and 3).
// The current session is the refresh-token family in the access token's sid claim: the
// refresh cookie is scoped to /auth and never reaches /me/*.
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordUpdater passwordUpdater;
    private final RefreshTokenService refreshTokenService;
    private final IdentityAuditService identityAuditService;

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        JwtUserPrincipal principal = currentUserService.principal();
        User user = currentUserService.requireActiveUser();

        if (user.getPassword() == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
        passwordUpdater.replace(user, newPassword);

        revokeOtherSessions(user, principal);
        record(IdentityAuditEventType.PASSWORD_CHANGED, principal);
    }

    @Transactional
    public void signOutOtherDevices() {
        JwtUserPrincipal principal = currentUserService.principal();
        User user = currentUserService.requireActiveUser();

        revokeOtherSessions(user, principal);
        record(IdentityAuditEventType.SIGNED_OUT_OTHER_DEVICES, principal);
    }

    // A token without sid cannot name its session, so it keeps none: fail towards signing out.
    private void revokeOtherSessions(User user, JwtUserPrincipal principal) {
        if (principal.sessionId() == null) {
            refreshTokenService.revokeAllFor(user);
            return;
        }
        refreshTokenService.revokeAllExcept(user, UUID.fromString(principal.sessionId()));
    }

    private void record(IdentityAuditEventType type, JwtUserPrincipal principal) {
        identityAuditService.recordEvent(type, principal.restaurantId(), principal.id(), principal.id(), Map.of());
    }
}
