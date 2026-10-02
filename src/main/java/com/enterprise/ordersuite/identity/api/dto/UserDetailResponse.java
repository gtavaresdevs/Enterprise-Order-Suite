package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

public record UserDetailResponse(
        Long id,
        String email,
        String role,
        boolean active,
        String firstName,
        String lastName,
        Instant createdAt,
        Instant updatedAt
) {}