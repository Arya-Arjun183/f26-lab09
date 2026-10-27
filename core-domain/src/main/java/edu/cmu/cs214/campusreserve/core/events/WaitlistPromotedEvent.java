package edu.cmu.cs214.campusreserve.core.events;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import java.time.Instant;
import java.util.Objects;

/**
 * A waitlist entry turned into a booking. The event carries the entry it came
 * from so readers can tell a promotion from an ordinary booking.
 */
public record WaitlistPromotedEvent(
    Instant occurredAt,
    String bookingId,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees,
    String waitlistEntryId,
    long waitlistSequence) implements DomainEvent {

    /** The wire name of this event. */
    public static final String TYPE = "waitlist-promoted";

    public WaitlistPromotedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(waitlistEntryId, "waitlistEntryId");
    }

    /** Builds the event from the entry and the booking it became. */
    public static WaitlistPromotedEvent of(WaitlistEntry entry, Booking booking, Instant occurredAt) {
        return new WaitlistPromotedEvent(
            occurredAt,
            booking.id(),
            booking.roomId(),
            booking.userId(),
            booking.interval(),
            booking.attendees(),
            entry.id(),
            entry.sequence());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
