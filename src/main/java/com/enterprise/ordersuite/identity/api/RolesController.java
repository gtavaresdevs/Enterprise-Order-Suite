package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.RoleResponse;
import com.enterprise.ordersuite.identity.application.RoleQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RolesController {

    private final RoleQueryService roleQueryService;

    public RolesController(RoleQueryService roleQueryService) {
        this.roleQueryService = roleQueryService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/roles")
    public List<RoleResponse> list() {
        return roleQueryService.listRoles();
    }
}