package com.enterprise.ordersuite.identity.api.dto;


import java.time.Instant;

public record MeResponse(
        String id,
        String email,
        String role,
        Instant createdAt,
        Instant updatedAt
) {}
