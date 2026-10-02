package com.store.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Test-only clock that can be advanced manually to exercise reservation expiration. */
final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    static MutableClock startingAt(Instant instant) {
        return new MutableClock(instant, ZoneId.of("UTC"));
    }

    void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
