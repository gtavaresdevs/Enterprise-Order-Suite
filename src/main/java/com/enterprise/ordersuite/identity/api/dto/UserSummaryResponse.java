package com.enterprise.ordersuite.identity.api.dto;

import java.time.LocalDateTime;

public record UserSummaryResponse(
        Long id,
        String email,
        String role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}