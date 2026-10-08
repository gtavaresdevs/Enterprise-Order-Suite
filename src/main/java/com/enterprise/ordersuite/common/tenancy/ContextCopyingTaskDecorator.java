package com.enterprise.ordersuite.common.tenancy;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.Optional;

// Carries the tenant context and the log MDC from the submitting thread onto the worker
// thread (§5.1.4), and restores the worker's own state afterwards.
public class ContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Optional<TenantContext> tenant = TenantContextHolder.get();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            Optional<TenantContext> previousTenant = TenantContextHolder.get();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            tenant.ifPresentOrElse(TenantContextHolder::set, TenantContextHolder::clear);
            restore(mdc);
            try {
                runnable.run();
            } finally {
                previousTenant.ifPresentOrElse(TenantContextHolder::set, TenantContextHolder::clear);
                restore(previousMdc);
            }
        };
    }

    private static void restore(Map<String, String> mdc) {
        if (mdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdc);
        }
    }
}
