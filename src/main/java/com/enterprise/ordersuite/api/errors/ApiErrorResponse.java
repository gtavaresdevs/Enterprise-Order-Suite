package com.enterprise.ordersuite.api.errors;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<String> errors // Added for validation errors
) {
    public ApiErrorResponse(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null);
    }
}
