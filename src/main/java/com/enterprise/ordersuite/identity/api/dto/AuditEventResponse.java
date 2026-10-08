package com.enterprise.ordersuite.identity.api.dto;

import java.time.Instant;
import java.util.Map;

// Draft spec, schema AuditEvent. Names are the users' current names.
public record AuditEventResponse(
        String id,
        String restaurantId,
        String type,
        String actorUserId,
        String actorName,
        String targetUserId,
        String targetName,
        Map<String, Object> details,
        Instant occurredAt
) {
}
