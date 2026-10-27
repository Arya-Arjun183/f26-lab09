package edu.cmu.cs214.campusreserve.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A booking: one user holds one room over one interval.
 *
 * <p>Bookings reference the room and the user by id rather than by object, so a
 * stored booking stays valid when a room is renamed or taken out of service.
 *
 * <p>{@code modifications} is what the booking used to be, oldest first. It is
 * empty for a booking nobody has changed, and it grows by one entry every time
 * the booker moves the booking or changes the party size.
 */
public record Booking(
    String id,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees,
    BookingStatus status,
    Instant createdAt,
    List<BookingModification> modifications) {

    public Booking {
        id = Identifiers.require(id, "booking id");
        roomId = Identifiers.require(roomId, "room id");
        userId = Identifiers.require(userId, "user id");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        modifications = List.copyOf(Objects.requireNonNull(modifications, "modifications"));
        if (attendees <= 0) {
            throw new IllegalArgumentException("Attendees must be positive: " + attendees);
        }
    }

    /** Builds a confirmed booking with nothing changed about it yet. */
    public static Booking confirmed(
        String id, String roomId, String userId, TimeInterval interval, int attendees, Instant createdAt) {
        return new Booking(
            id, roomId, userId, interval, attendees, BookingStatus.CONFIRMED, createdAt, List.of());
    }

    /** Returns a cancelled copy of this booking, history and all. */
    public Booking cancelled() {
        return new Booking(
            id, roomId, userId, interval, attendees, BookingStatus.CANCELLED, createdAt, modifications);
    }

    /**
     * Returns a copy holding {@code newInterval} for {@code newAttendees}, with
     * the change appended to the history.
     *
     * @throws IllegalArgumentException if neither the interval nor the party size
     *     is different, because that is not a change worth recording
     */
    public Booking modifiedTo(TimeInterval newInterval, int newAttendees, Instant at) {
        BookingModification change =
            new BookingModification(at, interval, attendees, newInterval, newAttendees);
        List<BookingModification> history = new ArrayList<>(modifications);
        history.add(change);
        return new Booking(
            id, roomId, userId, newInterval, newAttendees, status, createdAt, history);
    }

    /** The most recent change to this booking, if it has been changed at all. */
    public Optional<BookingModification> lastModification() {
        return modifications.isEmpty()
            ? Optional.empty()
            : Optional.of(modifications.get(modifications.size() - 1));
    }

    /** Does this booking still hold the room? */
    public boolean holdsRoom() {
        return status.holdsRoom();
    }

    /** Is {@code candidateUserId} the booker? */
    public boolean isOwnedBy(String candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /** Has the booking started by {@code now}? */
    public boolean hasStartedBy(Instant now) {
        return !interval.start().isAfter(now);
    }

    /** How long the booking runs, in whole minutes. */
    public long durationMinutes() {
        return interval.durationMinutes();
    }
}
