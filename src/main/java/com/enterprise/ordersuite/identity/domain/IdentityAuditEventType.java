package com.enterprise.ordersuite.identity.domain;

// Tenancy & Identity contract, "Audit event". Build 4 adds PIX_KEY_CHANGED and
// WHATSAPP_NUMBER_CHANGED.
public enum IdentityAuditEventType {
    MEMBER_INVITED,
    INVITE_RESENT,
    ROLE_CHANGED,
    MEMBER_DEACTIVATED,
    MEMBER_REACTIVATED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    SIGNED_OUT_OTHER_DEVICES,
    RESTAURANT_CREATED,
    RESTAURANT_UPDATED
}
