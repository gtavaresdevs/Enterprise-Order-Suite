package com.enterprise.ordersuite.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SetUserRoleRequest(
        @NotBlank String role
) {}