package com.enterprise.ordersuite.identity.api.dto;

import java.time.LocalDateTime;

public record UpdateMeResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        LocalDateTime updatedAt
) {}
