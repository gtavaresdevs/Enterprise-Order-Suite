package com.enterprise.ordersuite.api.errors;

import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidPasswordResetTokenException;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import com.enterprise.ordersuite.auth.service.exceptions.PasswordReuseException; // Imported
import com.enterprise.ordersuite.orders.domain.exception.InvalidStatusTransitionException;
import com.enterprise.ordersuite.orders.domain.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class AuthExceptionHandler {

  private final Clock clock;

  public AuthExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  // -------- Order / Product Errors --------

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
    return build(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION", ex.getMessage());
  }

  // -------- Login / authentication --------

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
    return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
    return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
  }

  // -------- Password reset token & policy restrictions --------

  @ExceptionHandler(InvalidPasswordResetTokenException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Invalid or expired reset token");
  }

  // Explicitly handling the custom historical reuse error mapping rule
  @ExceptionHandler(PasswordReuseException.class)
  public ResponseEntity<ApiErrorResponse> handlePasswordReuse(PasswordReuseException ex) {
    return build(
      HttpStatus.BAD_REQUEST,
      "PASSWORD_REUSE_ERROR",
      ex.getMessage() // Transmits: "This password has been used before. Please choose a different password."
    );
  }

  // -------- Refresh token --------

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_REFRESH_TOKEN", "Invalid refresh token");
  }

  // -------- Validation / bad input --------

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> errors = ex.getBindingResult().getFieldErrors().stream()
      .map(FieldError::getDefaultMessage)
      .collect(Collectors.toList());

    ApiErrorResponse body = new ApiErrorResponse(
      "INVALID_INPUT",
      "Validation failed",
      Instant.now(clock),
      errors
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Malformed JSON");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
  }

  // -------- Security Access Denied --------

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<AccessDeniedException> handleAccessDenied(AccessDeniedException ex) {
    throw ex; // Let Spring Security filters handle AccessDeniedException natively
  }

  // -------- Fallback (auth-safe) --------

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
    return build(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "AUTH_ERROR",
      "Authentication request failed"
    );
  }

  // -------- Helper --------

  private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
    ApiErrorResponse body = new ApiErrorResponse(
      code,
      message,
      Instant.now(clock),
      null
    );
    return ResponseEntity.status(status).body(body);
  }
}
