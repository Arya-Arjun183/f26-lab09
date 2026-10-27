package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import edu.cmu.cs214.campusreserve.core.events.BookingCancelledEvent;
import edu.cmu.cs214.campusreserve.core.events.BookingCreatedEvent;
import edu.cmu.cs214.campusreserve.core.events.WaitlistPromotedEvent;
import java.time.Instant;

/** Builders shared by the storage tests. */
final class StorageFixtures {

    static final String DAY = "2026-10-20";
    static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");

    private StorageFixtures() {
    }

    static TimeInterval interval(String startTime, String endTime) {
        return TimeInterval.parse(DAY + "T" + startTime + ":00Z", DAY + "T" + endTime + ":00Z");
    }

    static Room room(String id) {
        return Room.active(id, "Room " + id, "Sorrells", 10, RoomKind.STUDY_ROOM);
    }

    static Room room(String id, String building, int capacity, RoomKind kind) {
        return Room.active(id, "Room " + id, building, capacity, kind);
    }

    static User user(String id) {
        return new User(id, "User " + id, id + "@andrew.cmu.edu");
    }

    static Booking booking(String id, String roomId, String userId, String startTime, String endTime) {
        return Booking.confirmed(id, roomId, userId, interval(startTime, endTime), 4, NOW);
    }

    static WaitlistEntry entry(String id, String roomId, String userId, long sequence) {
        return new WaitlistEntry(id, roomId, userId, interval("10:00", "11:00"), 4, sequence, NOW);
    }

    static BookingCreatedEvent created(String bookingId) {
        return BookingCreatedEvent.of(booking(bookingId, "r-1", "u-1", "10:00", "11:00"), NOW);
    }

    static BookingCancelledEvent cancelled(String bookingId) {
        return BookingCancelledEvent.of(booking(bookingId, "r-1", "u-1", "10:00", "11:00"), NOW);
    }

    static WaitlistPromotedEvent promoted(String bookingId, String entryId, long sequence) {
        return WaitlistPromotedEvent.of(
            entry(entryId, "r-1", "u-2", sequence),
            booking(bookingId, "r-1", "u-2", "10:00", "11:00"),
            NOW);
    }
}
