package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;


import java.time.Clock;
import java.time.Instant;

@ControllerAdvice
public class AuthExceptionHandler {

    private final Clock clock;

    public AuthExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // -------- Password reset token --------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        );
    }

    // -------- Login / authentication --------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        );
    }

    // -------- Validation / bad input --------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                ex.getMessage()
        );
    }

    // -------- Fallback (auth-safe) --------

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(
            RuntimeException ex
    ) {
        // We do NOT leak internal details
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "AUTH_ERROR",
                "Authentication request failed"
        );
    }

    // -------- Helper --------

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                code,
                message,
                Instant.now(clock)
        );
        return ResponseEntity.status(status).body(body);
    }
}
