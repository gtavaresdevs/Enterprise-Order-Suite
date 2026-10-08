package com.enterprise.ordersuite.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// D12: production-safe defaults; local HTTP development opts out of Secure explicitly.
@ConfigurationProperties(prefix = "security.refresh-cookie")
public record RefreshCookieProperties(
    @DefaultValue("true") boolean secure,
    @DefaultValue("Lax") String sameSite
) {
    public static final String COOKIE_NAME = "refreshToken";
}
