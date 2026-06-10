package com.enterprise.ordersuite.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(
  @DefaultValue("true") boolean enabled,
  LimiterConfig forgotPassword,
  LimiterConfig login,
  LimiterConfig refresh,
  LimiterConfig logout,
  LimiterConfig resetPassword
) {
  public record LimiterConfig(
    @DefaultValue("5") int capacity,
    @DefaultValue("10") int refillSeconds
  ) {}
}
