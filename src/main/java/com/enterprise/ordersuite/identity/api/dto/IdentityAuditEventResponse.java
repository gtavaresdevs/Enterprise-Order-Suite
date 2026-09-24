package com.enterprise.ordersuite.identity.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record IdentityAuditEventResponse(
        Long id,
        String type,
        Long actorUserId,
        Long targetUserId,
        JsonNode metadata,
        Instant createdAt
) {}
