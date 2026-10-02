package com.enterprise.ordersuite.auth.dtos;

// Optional since Phase 1: the refresh token normally arrives in the HttpOnly cookie.
// The body field is the backward-compatible fallback, removed in Phase 6.
public record RefreshRequest(String refreshToken) {

}
