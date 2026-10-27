package edu.cmu.cs214.campusreserve.notifications.support;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.events.BookingCancelledEvent;
import edu.cmu.cs214.campusreserve.core.events.BookingCreatedEvent;
import edu.cmu.cs214.campusreserve.core.events.WaitlistPromotedEvent;
import edu.cmu.cs214.campusreserve.notifications.Channel;
import edu.cmu.cs214.campusreserve.notifications.InMemoryUserDirectory;
import edu.cmu.cs214.campusreserve.notifications.Notification;
import edu.cmu.cs214.campusreserve.notifications.NotificationKind;
import edu.cmu.cs214.campusreserve.notifications.Recipient;
import java.time.Instant;

/** Shared values so each test only spells out what it is actually about. */
public final class Fixtures {

    /** The user every fixture belongs to. */
    public static final String USER_ID = "u-dana";

    /** The room every fixture books. */
    public static final String ROOM_ID = "r-401";

    /** The booking every fixture is about. */
    public static final String BOOKING_ID = "b-1001";

    /** When the fixture events happened. */
    public static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");

    private Fixtures() {
    }

    /** Contact details for the fixture user. */
    public static Recipient recipient() {
        return new Recipient(USER_ID, "Dana Reyes", "dana@example.edu");
    }

    /** A directory holding the fixture user and nobody else. */
    public static InMemoryUserDirectory directory() {
        return InMemoryUserDirectory.empty().add(recipient());
    }

    /** An hour on the morning of 20 October 2026. */
    public static TimeInterval interval() {
        return TimeInterval.parse("2026-10-20T10:00:00Z", "2026-10-20T11:00:00Z");
    }

    /** A confirmed booking over {@link #interval()}. */
    public static Booking booking() {
        return Booking.confirmed(BOOKING_ID, ROOM_ID, USER_ID, interval(), 4, NOW);
    }

    /** The event for a booking that just took a room. */
    public static BookingCreatedEvent created() {
        return BookingCreatedEvent.of(booking(), NOW);
    }

    /** The event for a booking that just gave its room back. */
    public static BookingCancelledEvent cancelled() {
        return BookingCancelledEvent.of(booking(), NOW);
    }

    /** The event for a waiting-list entry that became a booking. */
    public static WaitlistPromotedEvent promoted() {
        return new WaitlistPromotedEvent(
            NOW, BOOKING_ID, ROOM_ID, USER_ID, interval(), 4, "w-7", 2L);
    }

    /** A ready-made message, for the parts of delivery that do not care about wording. */
    public static Notification notification(String id) {
        return new Notification(
            id, USER_ID, "dana@example.edu", Channel.EMAIL, NotificationKind.BOOKING_CONFIRMED,
            "Room r-401 is booked", "Hi Dana, room r-401 is yours.", NOW);
    }
}
