package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.service.RefreshTokenService;
import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final RefreshCookieProperties properties;

    public ResponseCookie issue(String rawToken, String contextPath) {
        return base(rawToken, contextPath).maxAge(RefreshTokenService.REFRESH_TTL).build();
    }

    public ResponseCookie clear(String contextPath) {
        return base("", contextPath).maxAge(Duration.ZERO).build();
    }

    // Path is derived from the request's context path, which is env-bound (SERVER_CONTEXT_PATH).
    private ResponseCookie.ResponseCookieBuilder base(String value, String contextPath) {
        return ResponseCookie.from(RefreshCookieProperties.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(contextPath + "/auth");
    }
}
