package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.User;

// Implemented by the auth module, which owns invite tokens (dependency inversion).
public interface MemberInvitations {

    // Issues a new invite token, invalidating earlier ones, and emails it after the caller's
    // transaction commits.
    void sendInvite(User user);
}
