package edu.cmu.cs214.campusreserve.core.events;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingModification;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.util.Objects;

/**
 * A booking changed its interval, its party size, or both. The event carries
 * what the booking was before the change as well as what it is now, so a reader
 * can tell one from the other without keeping its own history.
 */
public record BookingModifiedEvent(
    Instant occurredAt,
    String bookingId,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees,
    TimeInterval previousInterval,
    int previousAttendees) implements DomainEvent {

    /** The wire name of this event. */
    public static final String TYPE = "booking-modified";

    public BookingModifiedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(previousInterval, "previousInterval");
    }

    /** Builds the event from the booking as stored and the change it just took. */
    public static BookingModifiedEvent of(
        Booking booking, BookingModification change, Instant occurredAt) {
        Objects.requireNonNull(change, "change");
        return new BookingModifiedEvent(
            occurredAt,
            booking.id(),
            booking.roomId(),
            booking.userId(),
            booking.interval(),
            booking.attendees(),
            change.previousInterval(),
            change.previousAttendees());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
