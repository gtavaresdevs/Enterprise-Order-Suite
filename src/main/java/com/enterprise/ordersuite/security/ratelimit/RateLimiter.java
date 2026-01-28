package com.enterprise.ordersuite.security.ratelimit;

public interface RateLimiter {
    RateLimitDecision check(String key);
}
