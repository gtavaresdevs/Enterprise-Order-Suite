package com.enterprise.ordersuite.common.tenancy;

import java.util.Optional;

// Thread-bound like SecurityContextHolder: set by TenantContextFilter for the request, copied
// onto worker threads by ContextCopyingTaskDecorator, and cleared when either ends.
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static Optional<TenantContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }

    // The only way restaurant-scoped code reads its restaurant (§5.1.2). Fails closed.
    public static String requireRestaurantId() {
        TenantContext context = CONTEXT.get();
        if (context == null || context.restaurantId() == null) {
            throw new TenantContextMissingException();
        }
        return context.restaurantId();
    }
}
