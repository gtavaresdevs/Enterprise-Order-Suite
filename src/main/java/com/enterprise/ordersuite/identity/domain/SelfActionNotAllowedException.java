package com.enterprise.ordersuite.identity.domain;

// 409 SELF_ACTION_NOT_ALLOWED: nobody changes their own role or deactivates themselves (D-16).
public class SelfActionNotAllowedException extends RuntimeException {

    public SelfActionNotAllowedException() {
        super("You cannot do this to your own account");
    }
}
