package com.enterprise.ordersuite.identity.api.dto;

import java.time.LocalDateTime;

public record UserDetailResponse(
        Long id,
        String email,
        String role,
        boolean active,
        String firstName,
        String lastName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}