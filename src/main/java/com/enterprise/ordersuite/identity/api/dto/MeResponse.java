package com.enterprise.ordersuite.identity.api.dto;


import java.time.LocalDateTime;

public record MeResponse(
        Long id,
        String email,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
