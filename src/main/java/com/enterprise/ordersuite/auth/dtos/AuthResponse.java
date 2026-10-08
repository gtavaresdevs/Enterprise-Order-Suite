package com.enterprise.ordersuite.auth.dtos;

import com.enterprise.ordersuite.identity.application.SessionUser;

import java.time.Instant;

// The refresh token never appears in a body: it travels only in the HttpOnly cookie (D-10).
// expiresAt is the access token's expiry; user is the app shell's display data (D-20).
public record AuthResponse(String accessToken, Instant expiresAt, String role, SessionUser user) {
}
