package com.enterprise.ordersuite.identity.api.dto;

import java.time.LocalDateTime;

public record AdminUpdateUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDateTime updatedAt
) {}
