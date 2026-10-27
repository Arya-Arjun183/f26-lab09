package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingModification;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;

/**
 * How the domain records look on the wire.
 *
 * <p>Every response body is built here, so a field name appears in exactly one
 * place. Instants go out as ISO-8601 in UTC, which is how they are stored.
 */
public final class JsonViews {

    private JsonViews() {
    }

    /** A room. */
    public static JsonObject room(Room room) {
        return JsonObject.create()
            .field("id", room.id())
            .field("name", room.name())
            .field("building", room.building())
            .field("capacity", room.capacity())
            .field("kind", room.kind().name())
            .field("kindLabel", room.kind().displayName())
            .field("active", room.active());
    }

    /** A booking. */
    public static JsonObject booking(Booking booking) {
        return JsonObject.create()
            .field("id", booking.id())
            .field("roomId", booking.roomId())
            .field("userId", booking.userId())
            .field("start", booking.interval().start().toString())
            .field("end", booking.interval().end().toString())
            .field("minutes", booking.durationMinutes())
            .field("attendees", booking.attendees())
            .field("status", booking.status().name())
            .field("createdAt", booking.createdAt().toString())
            .field("modifications", JsonArray.of(booking.modifications(), JsonViews::modification));
    }

    /** One change in a booking's history: what it was, and what it became. */
    public static JsonObject modification(BookingModification change) {
        return JsonObject.create()
            .field("at", change.at().toString())
            .field("previousStart", change.previousInterval().start().toString())
            .field("previousEnd", change.previousInterval().end().toString())
            .field("previousAttendees", change.previousAttendees())
            .field("start", change.interval().start().toString())
            .field("end", change.interval().end().toString())
            .field("attendees", change.attendees());
    }

    /** A waitlist entry, with its arrival number. */
    public static JsonObject waitlistEntry(WaitlistEntry entry) {
        return JsonObject.create()
            .field("id", entry.id())
            .field("roomId", entry.roomId())
            .field("userId", entry.userId())
            .field("start", entry.interval().start().toString())
            .field("end", entry.interval().end().toString())
            .field("attendees", entry.attendees())
            .field("sequence", entry.sequence())
            .field("requestedAt", entry.requestedAt().toString());
    }

    /** A stretch of time. */
    public static JsonObject interval(TimeInterval interval) {
        return JsonObject.create()
            .field("start", interval.start().toString())
            .field("end", interval.end().toString())
            .field("minutes", interval.durationMinutes());
    }

    /** A waitlist entry that turned into a booking. */
    public static JsonObject promotion(CancellationResult.Promotion promotion) {
        return JsonObject.create()
            .field("waitlistEntryId", promotion.entry().id())
            .field("booking", booking(promotion.booking()));
    }
}
