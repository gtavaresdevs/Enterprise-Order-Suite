package com.enterprise.ordersuite.identity.domain;

// 409 INVITATION_NOT_PENDING: the member already set a password, or is deactivated and must be reactivated first.
public class InvitationNotPendingException extends RuntimeException {

    public InvitationNotPendingException() {
        super("This member has no pending invitation");
    }
}
