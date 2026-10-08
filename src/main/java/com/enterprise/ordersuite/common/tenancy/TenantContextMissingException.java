package com.enterprise.ordersuite.common.tenancy;

// Restaurant-scoped code ran without a restaurant. Always a bug: the caller must fail closed
// rather than fall back to "all restaurants" (ADR-0001).
public class TenantContextMissingException extends IllegalStateException {

    public TenantContextMissingException() {
        super("No restaurant in the tenant context");
    }
}
