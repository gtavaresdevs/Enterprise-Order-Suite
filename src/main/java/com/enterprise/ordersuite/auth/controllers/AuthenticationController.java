package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
import com.enterprise.ordersuite.auth.dtos.ForgotPasswordRequest;
import com.enterprise.ordersuite.auth.dtos.ResetPasswordRequest;
import com.enterprise.ordersuite.auth.service.AuthTokens;
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


// Refresh and logout take no body: the refresh token arrives only in the HttpOnly cookie
// (D-10), so they declare no consumes and accept a request without a Content-Type.
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and token management")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory refreshCookieFactory;

    @Operation(summary = "Login: access token in the body, refresh token as an HttpOnly cookie")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(authenticationService.authenticate(request), httpRequest);
    }

    @Operation(summary = "Rotate the refresh cookie and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            HttpServletRequest httpRequest) {
        return withRefreshCookie(authenticationService.refresh(cookieToken), httpRequest);
    }

    @Operation(summary = "Logout by revoking the refresh token family and clearing the cookie (idempotent)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            HttpServletRequest httpRequest) {
        authenticationService.logout(cookieToken);
        ResponseCookie cleared = refreshCookieFactory.clear(httpRequest.getContextPath());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthTokens tokens, HttpServletRequest httpRequest) {
        ResponseCookie cookie = refreshCookieFactory.issue(tokens.refreshToken(), httpRequest.getContextPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), tokens.role(), tokens.user()));
    }

    @Operation(summary = "Send password reset email (if user exists)")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Set a new password with a reset or invite token")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getToken(),
                request.getNewPassword()
        );
        return ResponseEntity.ok().build();
    }
}
