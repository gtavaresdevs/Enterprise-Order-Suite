package com.enterprise.ordersuite.identity.domain;

// 404 MEMBER_NOT_FOUND: unknown member, or a member of another restaurant (Tenancy & Identity, Errors).
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException() {
        super("Member not found");
    }
}
