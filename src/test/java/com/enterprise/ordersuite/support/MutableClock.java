package com.enterprise.ordersuite.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public class MutableClock extends Clock {

    public Instant instant;
    public final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.instant = Objects.requireNonNull(initialInstant);
        this.zone = Objects.requireNonNull(zone);
    }

    public void plusSeconds() {
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
