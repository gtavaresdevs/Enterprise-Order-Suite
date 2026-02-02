package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
import com.enterprise.ordersuite.auth.dtos.ForgotPasswordRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.auth.dtos.ResetPasswordRequest;
import com.enterprise.ordersuite.auth.service.AuthenticationService;
import com.enterprise.ordersuite.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    @Operation(summary = "Register a new user and issue access + refresh tokens")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login and issue access + refresh tokens")
    @PostMapping(value= "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Rotate refresh token and issue a new access token")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authenticationService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout by revoking refresh token (idempotent)")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.ok().build();
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
