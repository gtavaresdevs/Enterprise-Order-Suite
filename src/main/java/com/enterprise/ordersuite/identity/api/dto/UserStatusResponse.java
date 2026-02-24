package com.enterprise.ordersuite.identity.api.dto;

public record UserStatusResponse(
        Long userId,
        boolean active
) {}
