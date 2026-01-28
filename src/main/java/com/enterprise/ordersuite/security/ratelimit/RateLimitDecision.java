package com.enterprise.ordersuite.security.ratelimit;

/**
 * Result of a rate limit check.
 * retryAfterSeconds is meaningful only when allowed == false.
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
