package edu.cmu.cs214.campusreserve.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.events.BookingModifiedEvent;
import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import edu.cmu.cs214.campusreserve.notifications.support.TestClock;
import org.junit.jupiter.api.Test;

/** The notice a booker gets when their booking changes. */
class BookingModifiedNoticeTest {

    private static final TimeInterval MOVED =
        TimeInterval.parse("2026-10-20T13:00:00Z", "2026-10-20T14:00:00Z");

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final TestClock clock = TestClock.at("2026-10-19T09:00:00Z");
    private final NotificationService service =
        new NotificationService(Fixtures.directory(), outbox, clock);

    @Test
    void theNoticeCarriesTheOldDetailsAndTheNewOnes() {
        service.publish(modified());

        Notification notification = outbox.peek().orElseThrow();
        assertEquals(NotificationKind.BOOKING_MODIFIED, notification.kind());
        assertEquals("Room r-401 is now booked for 2026-10-20 13:00 to 14:00 UTC",
            notification.subject());
        assertTrue(notification.body().contains("2026-10-20 10:00 to 11:00 UTC"),
            notification.body());
        assertTrue(notification.body().contains("2026-10-20 13:00 to 14:00 UTC"),
            notification.body());
        assertTrue(notification.body().contains("for 4 people"), notification.body());
        assertTrue(notification.body().contains("for 6 people"), notification.body());
        assertTrue(notification.body().contains(Fixtures.BOOKING_ID), notification.body());
        assertEquals(1L, service.queuedCount());
        assertEquals(0, service.skippedCount());
    }

    @Test
    void theEventPicksTheModifiedWording() {
        assertEquals(NotificationKind.BOOKING_MODIFIED, NotificationKind.forEvent(modified()));
        assertEquals(NotificationKind.BOOKING_MODIFIED,
            NotificationKind.fromCode("booking-modified"));
    }

    @Test
    void aBookingNobodyKnowsAboutIsSkippedRatherThanThrown() {
        NotificationService lonely =
            new NotificationService(UserDirectory.empty(), outbox, clock);
        lonely.publish(modified());

        assertEquals(1, lonely.skippedCount());
        assertEquals("booking-modified", lonely.skipped().get(0).eventType());
        assertTrue(outbox.isEmpty());
    }

    private static BookingModifiedEvent modified() {
        Booking moved = Fixtures.booking().modifiedTo(MOVED, 6, Fixtures.NOW);
        return BookingModifiedEvent.of(moved, moved.lastModification().orElseThrow(), Fixtures.NOW);
    }
}
