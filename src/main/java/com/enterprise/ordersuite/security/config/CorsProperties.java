package com.enterprise.ordersuite.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// D12. Comma-separated in the environment; also the allow-list for RefreshOriginFilter.
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(
    @DefaultValue("http://localhost:3000") List<String> allowedOrigins
) {
}
