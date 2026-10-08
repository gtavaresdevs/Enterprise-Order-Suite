package com.enterprise.ordersuite.security.access;

import com.enterprise.ordersuite.common.tenancy.TenantContext;
import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import org.springframework.stereotype.Component;

// Referenced from @PreAuthorize as @tenantAccess. TenantContextFilter sets support only for a
// platform admin's GET with a valid X-Support-Restaurant-Id (§5.4, D-15), so this is true only
// on the read operations that also name it in their annotation (x-roles
// PLATFORM_ADMIN_SUPPORT_READ).
@Component("tenantAccess")
public class TenantAccess {

    public boolean supportRead() {
        return TenantContextHolder.get().map(TenantContext::support).orElse(false);
    }
}
