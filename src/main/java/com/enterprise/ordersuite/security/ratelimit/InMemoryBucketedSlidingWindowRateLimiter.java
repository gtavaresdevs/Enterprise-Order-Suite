package com.enterprise.ordersuite.security.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding Window Counters using fixed-size time buckets.

 * Bucket size: 1 minute (by design).
 * Window size: N minutes (configured).

 * How it works:
 * - For each key, keep an array of N buckets (minute -> count).
 * - Each request increments the current minute bucket.
 * - To decide, sum counts from buckets whose minute is within the last N minutes.

 * This is an approximation: within the current minute, bursts are counted fully.
 */
public class InMemoryBucketedSlidingWindowRateLimiter implements RateLimiter {

    private static final long BUCKET_SECONDS = 60;

    private final int limit;
    private final int windowMinutes;
    private final Clock clock;

    private final Map<String, WindowState> stateByKey = new ConcurrentHashMap<>();

    public InMemoryBucketedSlidingWindowRateLimiter(int limit, int windowMinutes, Clock clock) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        if (windowMinutes <= 0) throw new IllegalArgumentException("windowMinutes must be > 0");
        this.limit = limit;
        this.windowMinutes = windowMinutes;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public RateLimitDecision check(String key) {
        Objects.requireNonNull(key, "key must not be null");

        long nowEpochSeconds = clock.instant().getEpochSecond();
        long nowMinute = nowEpochSeconds / BUCKET_SECONDS;

        WindowState state = stateByKey.computeIfAbsent(key, k -> new WindowState(windowMinutes));

        synchronized (state) {
            // Increment current bucket
            int idx = indexForMinute(nowMinute);
            state.ensureBucketMinute(idx, nowMinute);
            state.counts[idx]++;

            // Calculate total within window
            long cutoffMinuteExclusive = nowMinute - windowMinutes; // valid minutes are (cutoffMinuteExclusive, nowMinute]
            int total = 0;

            long oldestMinuteWithCount = Long.MAX_VALUE;

            for (int i = 0; i < windowMinutes; i++) {
                long bucketMinute = state.minutes[i];
                int bucketCount = state.counts[i];

                if (bucketCount <= 0) continue;

                if (bucketMinute > cutoffMinuteExclusive && bucketMinute <= nowMinute) {
                    total += bucketCount;
                    if (bucketMinute < oldestMinuteWithCount) {
                        oldestMinuteWithCount = bucketMinute;
                    }
                }
            }

            if (total <= limit) {
                return new RateLimitDecision(true, 0);
            }

            // Compute retry-after:
            // When will the oldest counted minute fall out of the window?
            // A bucket at minute M falls out when we reach minute (M + windowMinutes),
            // and specifically at the start of that minute boundary.
            long allowedAtMinute = oldestMinuteWithCount + windowMinutes;
            long allowedAtEpochSeconds = allowedAtMinute * BUCKET_SECONDS;

            long retryAfterSeconds = allowedAtEpochSeconds - nowEpochSeconds;
            if (retryAfterSeconds < 1) retryAfterSeconds = 1;

            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }

    private int indexForMinute(long minute) {
        return Math.floorMod(minute, windowMinutes);
    }

    private static final class WindowState {
        final long[] minutes;
        final int[] counts;

        WindowState(int windowMinutes) {
            this.minutes = new long[windowMinutes];
            this.counts = new int[windowMinutes];
            for (int i = 0; i < windowMinutes; i++) {
                this.minutes[i] = Long.MIN_VALUE;
                this.counts[i] = 0;
            }
        }

        void ensureBucketMinute(int idx, long minute) {
            if (minutes[idx] != minute) {
                minutes[idx] = minute;
                counts[idx] = 0;
            }
        }
    }
}
