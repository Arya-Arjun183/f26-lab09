package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.events.BookingCancelledEvent;
import edu.cmu.cs214.campusreserve.core.events.BookingCreatedEvent;
import edu.cmu.cs214.campusreserve.core.events.BookingModifiedEvent;
import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import edu.cmu.cs214.campusreserve.core.events.WaitlistPromotedEvent;
import java.util.Objects;

/**
 * Turns a domain event into the one line it occupies in the event log.
 *
 * <p>This is the only place the log's shape is decided. Anything that reads
 * events.jsonl is reading what this class writes, so a field added here is a
 * change to a published contract.
 *
 * <p>Every line carries the same core fields, in this order:
 * {@code event, occurredAt, bookingId, roomId, userId, start, end, attendees}.
 * Promotions add the entry they came from, and a modification adds the interval
 * the booking held before it. Instants are ISO-8601 in UTC.
 */
public final class EventLogFormat {

    private EventLogFormat() {
    }

    /** The JSON line for an event, with no trailing newline. */
    public static String toLine(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        JsonLine line = JsonLine.object()
            .field("event", event.type())
            .field("occurredAt", event.occurredAt().toString())
            .field("bookingId", event.bookingId())
            .field("roomId", event.roomId())
            .field("userId", event.userId())
            .field("start", event.interval().start().toString())
            .field("end", event.interval().end().toString())
            .field("attendees", event.attendees());

        if (event instanceof WaitlistPromotedEvent promoted) {
            line.field("waitlistEntryId", promoted.waitlistEntryId())
                .field("waitlistSequence", promoted.waitlistSequence());
        }
        if (event instanceof BookingModifiedEvent modified) {
            line.field("previousStart", modified.previousInterval().start().toString())
                .field("previousEnd", modified.previousInterval().end().toString())
                .field("previousAttendees", modified.previousAttendees());
        }
        return line.build();
    }

    /** The wire names the log can contain, in the order the platform declares them. */
    public static String[] eventTypes() {
        return new String[] {
            BookingCreatedEvent.TYPE,
            BookingCancelledEvent.TYPE,
            BookingModifiedEvent.TYPE,
            WaitlistPromotedEvent.TYPE,
        };
    }

    /** Is {@code type} a wire name this platform writes? */
    public static boolean isKnownType(String type) {
        for (String known : eventTypes()) {
            if (known.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
