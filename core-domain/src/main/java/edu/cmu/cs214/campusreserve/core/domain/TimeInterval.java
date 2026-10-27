package edu.cmu.cs214.campusreserve.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A half-open time interval {@code [start, end)} on the UTC timeline.
 *
 * <p>Both endpoints are whole minutes: the instants carry no seconds and no
 * nanoseconds. Every time in the platform is UTC. There is no local time zone
 * anywhere in the domain.
 *
 * <p>{@link #overlaps(TimeInterval)} is the only overlap implementation in the
 * platform. Room conflict checks, user double-booking checks, and availability
 * all route through it.
 */
public record TimeInterval(Instant start, Instant end) implements Comparable<TimeInterval> {

    /** Minutes in a day, used by the minute-of-day helpers. */
    public static final int MINUTES_PER_DAY = 24 * 60;

    public TimeInterval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        requireWholeMinute(start, "start");
        requireWholeMinute(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                "Interval must be non-empty: [" + start + ", " + end + ")");
        }
    }

    /** Builds an interval from two instants. */
    public static TimeInterval of(Instant start, Instant end) {
        return new TimeInterval(start, end);
    }

    /** Builds an interval that starts at {@code start} and runs for {@code minutes}. */
    public static TimeInterval ofMinutes(Instant start, int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + minutes);
        }
        return new TimeInterval(start, start.plus(minutes, ChronoUnit.MINUTES));
    }

    /** Parses two ISO-8601 instants, for example {@code 2026-10-19T14:00:00Z}. */
    public static TimeInterval parse(String startText, String endText) {
        return new TimeInterval(Instant.parse(startText), Instant.parse(endText));
    }

    /**
     * Do this interval and {@code other} share at least one minute?
     *
     * <p>Two half-open intervals overlap when each one starts before the other
     * ends. Intervals that only touch (one ends exactly when the next begins) do
     * not overlap, so back-to-back bookings are legal.
     */
    public boolean overlaps(TimeInterval other) {
        Objects.requireNonNull(other, "other");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** Is {@code instant} inside this interval, counting the start but not the end? */
    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    /** Does this interval end exactly where {@code other} begins, or the reverse? */
    public boolean touches(TimeInterval other) {
        Objects.requireNonNull(other, "other");
        return end.equals(other.start) || other.end.equals(start);
    }

    /** Is this interval fully inside {@code window}? */
    public boolean isWithin(TimeInterval window) {
        Objects.requireNonNull(window, "window");
        return !start.isBefore(window.start) && !end.isAfter(window.end);
    }

    /** Length of the interval in whole minutes. */
    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    /** Has the interval finished by {@code now}? */
    public boolean endsOnOrBefore(Instant instant) {
        return !end.isAfter(instant);
    }

    /** Does the interval begin at or after {@code instant}? */
    public boolean startsOnOrAfter(Instant instant) {
        return !start.isBefore(instant);
    }

    /** The UTC calendar date the interval starts on. */
    public LocalDate startDateUtc() {
        return LocalDate.ofInstant(start, ZoneOffset.UTC);
    }

    /**
     * Does the interval begin and end on the same UTC date? An interval that ends
     * exactly at midnight still counts as belonging to the day it started on.
     */
    public boolean isWithinOneUtcDay() {
        LocalDate startDate = startDateUtc();
        LocalDate endDate = LocalDate.ofInstant(end, ZoneOffset.UTC);
        if (startDate.equals(endDate)) {
            return true;
        }
        return endDate.equals(startDate.plusDays(1)) && minuteOfDay(end) == 0;
    }

    /** Minutes elapsed since UTC midnight at the start of the interval. */
    public int startMinuteOfDayUtc() {
        return minuteOfDay(start);
    }

    /**
     * Minutes elapsed since UTC midnight at the end of the interval. An interval
     * ending at midnight reports {@link #MINUTES_PER_DAY} rather than zero, so
     * opening-hour comparisons stay ordered.
     */
    public int endMinuteOfDayUtc() {
        int minute = minuteOfDay(end);
        if (minute == 0 && !startDateUtc().equals(LocalDate.ofInstant(end, ZoneOffset.UTC))) {
            return MINUTES_PER_DAY;
        }
        return minute;
    }

    /** Are both endpoints multiples of {@code minutes} past the hour? */
    public boolean isAlignedTo(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Alignment must be positive: " + minutes);
        }
        return startMinuteOfDayUtc() % minutes == 0 && minuteOfDay(end) % minutes == 0;
    }

    /** Orders intervals by start, then by end. */
    @Override
    public int compareTo(TimeInterval other) {
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }

    private static int minuteOfDay(Instant instant) {
        long secondsIntoDay = Math.floorMod(instant.getEpochSecond(), 86_400L);
        return (int) (secondsIntoDay / 60);
    }

    private static void requireWholeMinute(Instant instant, String field) {
        if (instant.getNano() != 0 || instant.getEpochSecond() % 60 != 0) {
            throw new IllegalArgumentException(
                "Times must land on whole minutes (" + field + " was " + instant + ")");
        }
    }
}
