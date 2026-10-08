package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;

// Implemented by the auth module, which owns refresh tokens (dependency inversion).
public interface MemberSessions {

    // Revokes every refresh-token family of the user (Membership changes 2).
    void revokeAllFor(User user);
}
