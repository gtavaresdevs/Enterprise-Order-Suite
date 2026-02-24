package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.IdentityAuditEventResponse;
import com.enterprise.ordersuite.identity.api.dto.PagedResponse;
import com.enterprise.ordersuite.identity.application.IdentityAuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityAuditController {

    private final IdentityAuditQueryService queryService;

    public IdentityAuditController(IdentityAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/admin/identity-audit")
    public PagedResponse<IdentityAuditEventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.list(page, size);
    }
}