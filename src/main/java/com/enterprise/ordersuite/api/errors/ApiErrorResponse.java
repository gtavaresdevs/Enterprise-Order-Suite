package com.enterprise.ordersuite.api.errors;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
