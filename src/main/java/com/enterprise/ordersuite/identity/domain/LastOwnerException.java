package com.enterprise.ordersuite.identity.domain;

// 409 LAST_OWNER: the change would leave the restaurant without an active owner (D-16).
public class LastOwnerException extends RuntimeException {

    public LastOwnerException() {
        super("A restaurant must keep at least one active owner");
    }
}
