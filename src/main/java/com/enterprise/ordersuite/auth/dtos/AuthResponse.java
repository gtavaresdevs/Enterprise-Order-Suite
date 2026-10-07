package com.enterprise.ordersuite.auth.dtos;

// The refresh token never appears in a body: it travels only in the HttpOnly cookie (D-10).
public record AuthResponse(String accessToken) {
}
