package com.enterprise.ordersuite.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBucketedSlidingWindowRateLimiterTest {

    @Test
    void allowsUpToLimitWithinWindow_thenDenies() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-23T12:00:10Z"), ZoneOffset.UTC);

        RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(3, 10, clock);

        assertThat(limiter.check("k").allowed()).isTrue();
        assertThat(limiter.check("k").allowed()).isTrue();
        assertThat(limiter.check("k").allowed()).isTrue();

        RateLimitDecision denied = limiter.check("k");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void afterWindowPasses_allowsAgain() {
        // Window = 2 minutes, limit = 2
        MutableClock clock = new MutableClock(Instant.parse("2026-01-23T12:00:10Z"), ZoneOffset.UTC);

        RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(2, 2, clock);

        assertThat(limiter.check("k").allowed()).isTrue();
        assertThat(limiter.check("k").allowed()).isTrue();

        RateLimitDecision denied = limiter.check("k");
        assertThat(denied.allowed()).isFalse();

        // Oldest minute bucket will fall out at:
        // allowedAtMinute = oldestMinute + windowMinutes
        // To guarantee we crossed the boundary, jump forward 2 minutes + 1 second.
        clock.plusSeconds();

        assertThat(limiter.check("k").allowed()).isTrue();
    }

    @Test
    void differentKeys_areIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-23T12:00:10Z"), ZoneOffset.UTC);

        RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(1, 10, clock);

        assertThat(limiter.check("a").allowed()).isTrue();
        assertThat(limiter.check("a").allowed()).isFalse();

        assertThat(limiter.check("b").allowed()).isTrue();
        assertThat(limiter.check("b").allowed()).isFalse();
    }

    @Test
    void retryAfter_isAtLeastOneSecond_whenDenied() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-23T12:00:59Z"), ZoneOffset.UTC);

        RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(1, 1, clock);

        assertThat(limiter.check("k").allowed()).isTrue();

        RateLimitDecision denied = limiter.check("k");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }
}
