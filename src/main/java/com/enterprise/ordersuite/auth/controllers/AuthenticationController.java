package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
import com.enterprise.ordersuite.auth.dtos.ForgotPasswordRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.ResetPasswordRequest;
import com.enterprise.ordersuite.auth.service.AuthenticationService;
import com.enterprise.ordersuite.auth.service.PasswordResetService;
import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;


@RestController
@RequestMapping(value = "/auth", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and token management")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory refreshCookieFactory;

    @Operation(summary = "Login and issue access + refresh tokens (refresh also as HttpOnly cookie)")
    @PostMapping(value= "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(authenticationService.authenticate(request), httpRequest);
    }

    @Operation(summary = "Rotate the refresh token (cookie, or body as fallback) and issue a new access token")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest) {
        String rawToken = RefreshTokenSource.resolve(cookieToken, request == null ? null : request.refreshToken());
        return withRefreshCookie(authenticationService.refresh(rawToken), httpRequest);
    }

    @Operation(summary = "Logout by revoking the refresh token family and clearing the cookie (idempotent)")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        authenticationService.logout(RefreshTokenSource.resolve(cookieToken, request == null ? null : request.refreshToken()));
        ResponseCookie cleared = refreshCookieFactory.clear(httpRequest.getContextPath());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    // The body still carries refreshToken until Phase 6; the cookie is the target transport.
    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response, HttpServletRequest httpRequest) {
        ResponseCookie cookie = refreshCookieFactory.issue(response.getRefreshToken(), httpRequest.getContextPath());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
    }

    @Operation(summary = "Send password reset email (if user exists)")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Reset password using reset token")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getToken(),
                request.getNewPassword()
        );
        return ResponseEntity.ok().build();
    }
}
