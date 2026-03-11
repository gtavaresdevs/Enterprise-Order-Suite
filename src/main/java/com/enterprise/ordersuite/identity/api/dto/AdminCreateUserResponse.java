package com.enterprise.ordersuite.identity.api.dto;

import java.time.LocalDateTime;

public record AdminCreateUserResponse(
        Long id,
        String email,
        String role,
        Boolean active,
        LocalDateTime createdAt
) {}