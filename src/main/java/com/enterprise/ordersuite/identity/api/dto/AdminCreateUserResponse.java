package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

public record AdminCreateUserResponse(
        Long id,
        String email,
        String role,
        Boolean active,
        Instant createdAt
) {}