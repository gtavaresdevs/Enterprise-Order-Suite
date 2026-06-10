package com.enterprise.ordersuite.security.ratelimit;

/**
 * A stateless implementation of RateLimiter that always allows requests.
 * Used in test environments to eliminate resource overhead and state persistence.
 */
public class NoOpRateLimiter implements RateLimiter {
  @Override
  public RateLimitDecision check(String key) {
    return new RateLimitDecision(true, 0);
  }
}
