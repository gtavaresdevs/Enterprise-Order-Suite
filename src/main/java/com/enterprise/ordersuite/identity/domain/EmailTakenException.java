package com.enterprise.ordersuite.identity.domain;

// 409 EMAIL_TAKEN: the email exists on the platform; the message never says in which restaurant (Invites 2).
public class EmailTakenException extends RuntimeException {

    public EmailTakenException() {
        super("This email is already in use");
    }
}
