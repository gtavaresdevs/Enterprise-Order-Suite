package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.orders.domain.exception.InvalidStatusTransitionException;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import com.enterprise.ordersuite.products.domain.exception.InsufficientStockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        log.warn("ProductNotFoundException: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                "PRODUCT_NOT_FOUND",
                ex.getMessage(),
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("InsufficientStockException: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                "INSUFFICIENT_STOCK",
                ex.getMessage(),
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        log.warn("InvalidStatusTransitionException: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                "INVALID_STATUS_TRANSITION",
                ex.getMessage(),
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException caught by GlobalExceptionHandler: {}", ex.getMessage(), ex);

        Throwable rootCause = findRootCause(ex);

        if (rootCause instanceof InsufficientStockException) {
            log.warn("Handling InsufficientStockException from root cause: {}", rootCause.getMessage());
            return handleInsufficientStock((InsufficientStockException) rootCause);
        }
        if (rootCause instanceof ProductNotFoundException) {
            log.warn("Handling ProductNotFoundException from root cause: {}", rootCause.getMessage());
            return handleProductNotFound((ProductNotFoundException) rootCause);
        }
        if (rootCause instanceof InvalidStatusTransitionException) {
            log.warn("Handling InvalidStatusTransitionException from root cause: {}", rootCause.getMessage());
            return handleInvalidStatusTransition((InvalidStatusTransitionException) rootCause);
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
