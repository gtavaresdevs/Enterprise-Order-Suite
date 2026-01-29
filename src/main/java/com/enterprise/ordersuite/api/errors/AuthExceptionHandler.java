package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Clock;
import java.time.Instant;

@ControllerAdvice
public class AuthExceptionHandler {

    private final Clock clock;

    public AuthExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // -------- Login / authentication --------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password"
        );
    }

    // -------- Password reset token --------

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_RESET_TOKEN",
                "Invalid or expired reset token"
        );
    }

    // -------- Refresh token --------

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REFRESH_TOKEN",
                "Invalid refresh token"
        );
    }

    // -------- Validation / bad input --------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                "Invalid input"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                "Malformed JSON"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                ex.getMessage()
        );
    }

    // -------- Fallback (auth-safe) --------

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
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
