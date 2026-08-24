package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBucketedSlidingWindowRateLimiterTest {

  @Test
  void allowsUpToLimitWithinWindow_thenDenies() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(3, 10, clock);

    assertThat(limiter.check("k").allowed()).isTrue();
    assertThat(limiter.check("k").allowed()).isTrue();
    assertThat(limiter.check("k").allowed()).isTrue();

    RateLimitDecision denied = limiter.check("k");

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isGreaterThan(0);
  }

  @Test
  void afterWindowPasses_allowsAgain() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(2, 2, clock);

    assertThat(limiter.check("k").allowed()).isTrue();
    assertThat(limiter.check("k").allowed()).isTrue();

    RateLimitDecision denied = limiter.check("k");

    assertThat(denied.allowed()).isFalse();

    clock.plusSeconds();

    assertThat(limiter.check("k").allowed()).isTrue();
  }

  @Test
  void differentKeys_areIndependent() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(1, 10, clock);

    assertThat(limiter.check("a").allowed()).isTrue();
    assertThat(limiter.check("a").allowed()).isFalse();

    assertThat(limiter.check("b").allowed()).isTrue();
    assertThat(limiter.check("b").allowed()).isFalse();
  }

  @Test
  void retryAfter_calculatesSecondsUntilOldestBucketExpires() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:59Z"),
      ZoneOffset.UTC
    );

    RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(1, 1, clock);

    assertThat(limiter.check("k").allowed()).isTrue();

    RateLimitDecision denied = limiter.check("k");

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(1);
  }

  @Test
  void nullKey_isRejected() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    RateLimiter limiter = new InMemoryBucketedSlidingWindowRateLimiter(3, 10, clock);

    assertThatThrownBy(() -> limiter.check(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("key must not be null");
  }

  @Test
  void zeroLimit_isRejected() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    assertThatThrownBy(
      () -> new InMemoryBucketedSlidingWindowRateLimiter(0, 10, clock)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("limit must be > 0");
  }

  @Test
  void negativeLimit_isRejected() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    assertThatThrownBy(
      () -> new InMemoryBucketedSlidingWindowRateLimiter(-1, 10, clock)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("limit must be > 0");
  }

  @Test
  void zeroWindowMinutes_isRejected() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    assertThatThrownBy(
      () -> new InMemoryBucketedSlidingWindowRateLimiter(3, 0, clock)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("windowMinutes must be > 0");
  }

  @Test
  void negativeWindowMinutes_isRejected() {
    MutableClock clock = new MutableClock(
      Instant.parse("2026-01-23T12:00:10Z"),
      ZoneOffset.UTC
    );

    assertThatThrownBy(
      () -> new InMemoryBucketedSlidingWindowRateLimiter(3, -1, clock)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("windowMinutes must be > 0");
  }
}
