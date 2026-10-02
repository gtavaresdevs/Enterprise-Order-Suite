package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;

public record UpdateMeResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Instant updatedAt
) {}
