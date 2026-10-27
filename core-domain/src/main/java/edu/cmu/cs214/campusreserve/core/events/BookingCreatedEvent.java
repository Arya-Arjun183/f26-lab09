package edu.cmu.cs214.campusreserve.core.events;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.util.Objects;

/** A booking now holds a room. */
public record BookingCreatedEvent(
    Instant occurredAt,
    String bookingId,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees) implements DomainEvent {

    /** The wire name of this event. */
    public static final String TYPE = "booking-created";

    public BookingCreatedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(interval, "interval");
    }

    /** Builds the event from the booking that was just stored. */
    public static BookingCreatedEvent of(Booking booking, Instant occurredAt) {
        return new BookingCreatedEvent(
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
