package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.common.tenancy.TenantContextMissingException;
import com.enterprise.ordersuite.identity.domain.InvalidAvatarException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

// Consulted after AuthExceptionHandler. This is the fallback advice: it owns the
// RuntimeException catch-all, so anything the first advice does not name lands here.
@Order(2)
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(InvalidAvatarException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAvatar(InvalidAvatarException ex) {
        log.warn("InvalidAvatarException: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                "INVALID_AVATAR",
                ex.getMessage(),
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // API conventions §10: an upload over the multipart limit.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ApiErrorResponse body = new ApiErrorResponse(
                "PAYLOAD_TOO_LARGE",
                "The upload exceeds the size limit",
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("MethodArgumentNotValidException: {}", details);
        ApiErrorResponse body = new ApiErrorResponse(
                "INVALID_INPUT",
                details,
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Always a bug (Tenancy & Identity §5.1.2): restaurant-scoped code ran without a
    // restaurant and failed closed.
    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantContextMissing(TenantContextMissingException ex) {
        log.error("BUG: restaurant-scoped code ran without a tenant context", ex);
        ApiErrorResponse body = new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected runtime error occurred.",
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException caught by GlobalExceptionHandler: {}", ex.getMessage(), ex);

        Throwable rootCause = findRootCause(ex);

        if (rootCause instanceof InvalidAvatarException) {
            log.warn("Handling InvalidAvatarException from root cause: {}", rootCause.getMessage());
            return handleInvalidAvatar((InvalidAvatarException) rootCause);
        }

        // Fallback for other RuntimeExceptions
        ApiErrorResponse body = new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected runtime error occurred.",
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private Throwable findRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            // Handle common Spring wrappers
            if (cause instanceof UndeclaredThrowableException || cause instanceof TransactionSystemException) {
                cause = cause.getCause();
            } else {
                // For other exceptions, just get the direct cause
                cause = cause.getCause();
            }
        }
        return cause;
    }
}
