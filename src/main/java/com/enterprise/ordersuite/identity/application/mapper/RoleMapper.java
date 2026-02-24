package com.enterprise.ordersuite.identity.application.mapper;

import com.enterprise.ordersuite.identity.api.dto.RoleResponse;
import com.enterprise.ordersuite.identity.domain.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {
    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}