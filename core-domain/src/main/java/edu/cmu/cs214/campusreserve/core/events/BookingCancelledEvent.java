package edu.cmu.cs214.campusreserve.core.events;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.util.Objects;

/** A booking gave its room back. */
public record BookingCancelledEvent(
    Instant occurredAt,
    String bookingId,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees) implements DomainEvent {

    /** The wire name of this event. */
    public static final String TYPE = "booking-cancelled";

    public BookingCancelledEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(interval, "interval");
    }

    /** Builds the event from the booking that was just cancelled. */
    public static BookingCancelledEvent of(Booking booking, Instant occurredAt) {
        return new BookingCancelledEvent(
            occurredAt,
            booking.id(),
            booking.roomId(),
            booking.userId(),
            booking.interval(),
            booking.attendees());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
