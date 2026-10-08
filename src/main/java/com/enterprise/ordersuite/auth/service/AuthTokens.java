package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.identity.application.SessionUser;

import java.time.Instant;

// What a sign-in or refresh issues. The controller puts everything but the refresh token in
// the body; the refresh token goes only in the HttpOnly cookie.
public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String role,
        SessionUser user,
        String refreshToken
) {
}
