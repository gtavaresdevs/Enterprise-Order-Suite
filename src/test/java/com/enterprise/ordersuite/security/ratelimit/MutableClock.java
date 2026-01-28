package com.enterprise.ordersuite.security.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant initialInstant, ZoneId zone) {
        this.instant = Objects.requireNonNull(initialInstant);
        this.zone = Objects.requireNonNull(zone);
    }

    void plusSeconds() {
        instant = instant.plusSeconds(121);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
