package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

public record AdminUpdateUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Instant updatedAt
) {}
