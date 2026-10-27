package edu.cmu.cs214.campusreserve.core.service;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.ports.BookingStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Turns a room's bookings into free and busy time.
 *
 * <p>Which bookings fall inside the window asked about comes from
 * {@link BookingStore#findConflicts(String, TimeInterval)}, which settles it in
 * one pass over the store. The gap arithmetic below only walks sorted endpoints.
 */
public final class AvailabilityService {

    private final BookingStore bookings;
    private final BookingPolicy policy;

    public AvailabilityService(BookingStore bookings, BookingPolicy policy) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Is nothing holding the room over {@code interval}? */
    public boolean isFree(String roomId, TimeInterval interval) {
        return bookings.findConflicts(roomId, interval).isEmpty();
    }

    /** The parts of {@code window} the room is already spoken for, ordered by start. */
    public List<TimeInterval> busyIntervals(String roomId, TimeInterval window) {
        Objects.requireNonNull(window, "window");
        return bookings.findConflicts(roomId, window).stream()
            .map(Booking::interval)
            .sorted()
            .toList();
    }

    /**
     * The parts of {@code window} nobody holds, ordered by start. Bookings that
     * run past the edges of the window are clipped to it.
     */
    public List<TimeInterval> freeIntervals(String roomId, TimeInterval window) {
        Objects.requireNonNull(window, "window");
        List<TimeInterval> busy = busyIntervals(roomId, window);

        List<TimeInterval> free = new ArrayList<>();
        Instant cursor = window.start();
        for (TimeInterval taken : busy) {
            Instant takenStart = later(taken.start(), window.start());
            Instant takenEnd = earlier(taken.end(), window.end());
            if (takenStart.isAfter(cursor)) {
                free.add(new TimeInterval(cursor, takenStart));
            }
            if (takenEnd.isAfter(cursor)) {
                cursor = takenEnd;
            }
        }
        if (cursor.isBefore(window.end())) {
            free.add(new TimeInterval(cursor, window.end()));
        }
        return List.copyOf(free);
    }

    /** The free time in the room during opening hours on a UTC date. */
    public List<TimeInterval> freeOnDate(String roomId, LocalDate date) {
        return freeIntervals(roomId, openingWindow(date));
    }

    /**
     * Free stretches on a date that are long enough to hold a booking of
     * {@code minutes}, with the leading edge aligned to the slot granularity.
     */
    public List<TimeInterval> openSlotsOnDate(String roomId, LocalDate date, int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + minutes);
        }
        List<TimeInterval> slots = new ArrayList<>();
        for (TimeInterval free : freeOnDate(roomId, date)) {
            Instant cursor = alignForward(free.start());
            while (!cursor.plus(minutes, ChronoUnit.MINUTES).isAfter(free.end())) {
                slots.add(TimeInterval.ofMinutes(cursor, minutes));
                cursor = cursor.plus(policy.slotGranularityMinutes(), ChronoUnit.MINUTES);
            }
        }
        return List.copyOf(slots);
    }

    /** Minutes of the opening window on a date that are already spoken for. */
    public long bookedMinutesOnDate(String roomId, LocalDate date) {
        return busyIntervals(roomId, openingWindow(date)).stream()
            .mapToLong(TimeInterval::durationMinutes)
            .sum();
    }

    /**
     * Share of the opening window on a date that is spoken for, between 0 and 1.
     */
    public double utilizationOnDate(String roomId, LocalDate date) {
        double open = policy.openMinutesPerDay();
        return open == 0 ? 0 : Math.min(1.0, bookedMinutesOnDate(roomId, date) / open);
    }

    /** The rooms with the most booked minutes on a date, busiest first. */
    public List<RoomLoad> busiestRooms(List<String> roomIds, LocalDate date) {
        Objects.requireNonNull(roomIds, "roomIds");
        return roomIds.stream()
            .map(roomId -> new RoomLoad(roomId, bookedMinutesOnDate(roomId, date)))
            .sorted(Comparator.comparingLong(RoomLoad::bookedMinutes).reversed()
                .thenComparing(RoomLoad::roomId))
            .toList();
    }

    /** How much of a day a room has sold. */
    public record RoomLoad(String roomId, long bookedMinutes) {
    }

    /** The opening window on a UTC date, taken from the booking policy. */
    public TimeInterval openingWindow(LocalDate date) {
        Objects.requireNonNull(date, "date");
        Instant midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TimeInterval(
            midnight.plus(policy.openingMinuteOfDayUtc(), ChronoUnit.MINUTES),
            midnight.plus(policy.closingMinuteOfDayUtc(), ChronoUnit.MINUTES));
    }

    private Instant alignForward(Instant instant) {
        int granularity = policy.slotGranularityMinutes();
        long minutes = Math.floorDiv(instant.getEpochSecond(), 60L);
        long remainder = Math.floorMod(minutes, (long) granularity);
        return remainder == 0 ? instant : instant.plus(granularity - remainder, ChronoUnit.MINUTES);
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
