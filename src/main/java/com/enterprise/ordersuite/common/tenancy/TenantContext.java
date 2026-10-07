package com.enterprise.ordersuite.common.tenancy;

// Who the current request acts for (Tenancy & Identity §5.1). restaurantId is null for a
// platform admin outside a support read; support marks a read-only support read (§5.4).
public record TenantContext(String restaurantId, String userId, String role, boolean support) {
}
