package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

public record UserSummaryResponse(
        Long id,
        String email,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}