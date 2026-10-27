package edu.cmu.cs214.campusreserve.core.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** A clock the tests move by hand. Nothing in the suite waits on real time. */
public final class TestClock extends Clock {

    private Instant instant;

    private TestClock(Instant instant) {
        this.instant = instant;
    }

    /** A clock parked at an ISO-8601 instant, for example {@code 2026-10-19T09:00:00Z}. */
    public static TestClock at(String isoInstant) {
        return new TestClock(Instant.parse(isoInstant));
    }

    /** Moves the clock forward. */
    public void advanceMinutes(long minutes) {
        instant = instant.plus(minutes, ChronoUnit.MINUTES);
    }

    /** Moves the clock forward by whole days. */
    public void advanceDays(long days) {
        instant = instant.plus(days, ChronoUnit.DAYS);
    }

    /** Parks the clock at a new instant. */
    public void setTo(String isoInstant) {
        instant = Instant.parse(isoInstant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
