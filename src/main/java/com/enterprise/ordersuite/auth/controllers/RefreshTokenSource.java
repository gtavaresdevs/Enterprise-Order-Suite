package com.enterprise.ordersuite.auth.controllers;

// D18: the cookie is the target source; the body is the backward-compatible fallback.
final class RefreshTokenSource {

    private RefreshTokenSource() {
    }

    static String resolve(String cookieValue, String bodyValue) {
        if (cookieValue != null && !cookieValue.isBlank()) {
            return cookieValue;
        }
        if (bodyValue != null && !bodyValue.isBlank()) {
            return bodyValue;
        }
        return null;
    }
}
