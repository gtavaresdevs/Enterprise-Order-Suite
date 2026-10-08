package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.common.tenancy.TenantContextMissingException;
import com.enterprise.ordersuite.identity.domain.EmailTakenException;
import com.enterprise.ordersuite.identity.domain.InvalidAvatarException;
import com.enterprise.ordersuite.identity.domain.InvitationNotPendingException;
import com.enterprise.ordersuite.identity.domain.LastOwnerException;
import com.enterprise.ordersuite.identity.domain.MemberNotFoundException;
import com.enterprise.ordersuite.identity.domain.SelfActionNotAllowedException;
import com.enterprise.ordersuite.restaurants.domain.RestaurantNotFoundException;
import com.enterprise.ordersuite.restaurants.domain.SlugReservedException;
import com.enterprise.ordersuite.restaurants.domain.SlugTakenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    // Tenancy & Identity, Errors.
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMemberNotFound(MemberNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRestaurantNotFound(RestaurantNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SlugTakenException.class)
    public ResponseEntity<ApiErrorResponse> handleSlugTaken(SlugTakenException ex) {
        return build(HttpStatus.CONFLICT, "SLUG_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(SlugReservedException.class)
    public ResponseEntity<ApiErrorResponse> handleSlugReserved(SlugReservedException ex) {
        return build(HttpStatus.BAD_REQUEST, "SLUG_RESERVED", ex.getMessage());
    }

    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailTaken(EmailTakenException ex) {
        return build(HttpStatus.CONFLICT, "EMAIL_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(LastOwnerException.class)
    public ResponseEntity<ApiErrorResponse> handleLastOwner(LastOwnerException ex) {
        return build(HttpStatus.CONFLICT, "LAST_OWNER", ex.getMessage());
    }

    @ExceptionHandler(SelfActionNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleSelfAction(SelfActionNotAllowedException ex) {
        return build(HttpStatus.CONFLICT, "SELF_ACTION_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(InvitationNotPendingException.class)
    public ResponseEntity<ApiErrorResponse> handleInvitationNotPending(InvitationNotPendingException ex) {
        return build(HttpStatus.CONFLICT, "INVITATION_NOT_PENDING", ex.getMessage());
    }

    // API conventions §10.2: a malformed id, a bad page or size, or a query value of the wrong
    // type is INVALID_INPUT, not a 500.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request parameter");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getName() + ": invalid value");
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

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, Instant.now(clock), null));
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
