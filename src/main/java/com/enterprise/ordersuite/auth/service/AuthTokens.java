package com.enterprise.ordersuite.auth.service;

// What a sign-in or refresh issues. The controller puts the access token in the body and the
// refresh token only in the HttpOnly cookie.
public record AuthTokens(String accessToken, String refreshToken) {
}
