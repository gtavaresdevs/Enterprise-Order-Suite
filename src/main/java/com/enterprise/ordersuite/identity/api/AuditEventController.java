package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.AuditEventResponse;
import com.enterprise.ordersuite.identity.application.IdentityAuditService;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The audit log of the caller's restaurant (§5.3: OWNER, MANAGER; the platform admin by
// support read).
@RestController
public class AuditEventController {

    private final IdentityAuditService identityAuditService;

    public AuditEventController(IdentityAuditService identityAuditService) {
        this.identityAuditService = identityAuditService;
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('MANAGER') or @tenantAccess.supportRead()")
    public ResponseEntity<PagedResult<AuditEventResponse>> list(
            @RequestParam(required = false) IdentityAuditEventType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(identityAuditService.list(type, page, size));
    }
}
