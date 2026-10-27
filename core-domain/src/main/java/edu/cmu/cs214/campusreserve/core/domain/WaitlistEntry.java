package edu.cmu.cs214.campusreserve.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A user waiting for a room over an interval that was taken when they asked.
 *
 * <p>{@code sequence} is a platform-wide increasing arrival number. Promotion
 * walks a room's entries in sequence order, so the wait is first in, first out.
 */
public record WaitlistEntry(
    String id,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees,
    long sequence,
    Instant requestedAt) {

    public WaitlistEntry {
        id = Identifiers.require(id, "waitlist entry id");
        roomId = Identifiers.require(roomId, "room id");
        userId = Identifiers.require(userId, "user id");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (attendees <= 0) {
            throw new IllegalArgumentException("Attendees must be positive: " + attendees);
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence must not be negative: " + sequence);
        }
    }

    /** Is this entry for the same room, user, and interval as {@code request}? */
    public boolean matches(BookingRequest request) {
        return roomId.equals(request.roomId())
            && userId.equals(request.userId())
            && interval.equals(request.interval());
    }

    /** Is the waited-for interval still in the future at {@code now}? */
    public boolean isUpcomingAt(Instant now) {
        return interval.start().isAfter(now);
    }
}
