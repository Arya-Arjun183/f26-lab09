package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import edu.cmu.cs214.campusreserve.core.events.WaitlistPromotedEvent;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingServiceWaitlistTest extends BookingServiceTestBase {

    private static final TimeInterval SLOT = Fixtures.interval("10:00", "11:00");

    @Test
    void aTakenSlotSendsTheAskerToTheWaitlist() {
        bookAndGetId("r-1", "u-1", SLOT);
        BookingOutcome outcome = book("r-1", "u-2", SLOT);
        assertTrue(outcome.isWaitlisted());
        WaitlistEntry entry = outcome.waitlistEntry().orElseThrow();
        assertEquals("u-2", entry.userId());
        assertEquals(SLOT, entry.interval());
        assertEquals(1, waitlist.count());
    }

    @Test
    void waitlistingPublishesNoEvent() {
        bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        assertEquals(List.of("booking-created"), publisher.types());
    }

    @Test
    void theSameAskerCannotJoinTwiceForTheSameSlot() {
        bookAndGetId("r-1", "u-1", SLOT);
        assertTrue(book("r-1", "u-2", SLOT).isWaitlisted());
        assertEquals(Optional.of(RejectionReason.ALREADY_ON_WAITLIST),
            book("r-1", "u-2", SLOT).rejectionReason());
        assertEquals(1, waitlist.count());
    }

    @Test
    void withTheWaitlistOffATakenSlotIsRefused() {
        build(BookingPolicy.defaults().withWaitlistEnabled(false));
        bookAndGetId("r-1", "u-1", SLOT);
        assertEquals(Optional.of(RejectionReason.ROOM_UNAVAILABLE),
            book("r-1", "u-2", SLOT).rejectionReason());
        assertEquals(0, waitlist.count());
    }

    @Test
    void entriesArriveInOrder() {
        bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);
        List<WaitlistEntry> queue = service.waitlistForRoom("r-1");
        assertEquals(List.of("u-2", "u-3"), queue.stream().map(WaitlistEntry::userId).toList());
        assertTrue(queue.get(0).sequence() < queue.get(1).sequence());
    }

    @Test
    void cancellationPromotesTheFirstWaiter() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertEquals(1, result.promotions().size());
        CancellationResult.Promotion promotion = result.promotions().get(0);
        assertEquals("u-2", promotion.booking().userId());
        assertEquals(SLOT, promotion.booking().interval());
        assertTrue(promotion.booking().holdsRoom());
        assertEquals(1, waitlist.count());
        assertEquals(List.of("u-3"), service.waitlistForRoom("r-1").stream()
            .map(WaitlistEntry::userId).toList());
    }

    @Test
    void promotionPublishesItsOwnEvent() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        service.cancelBooking(held, "u-1");

        assertEquals(List.of("booking-created", "booking-cancelled", "waitlist-promoted"), publisher.types());
        WaitlistPromotedEvent event = publisher.ofType(WaitlistPromotedEvent.class).get(0);
        assertEquals("u-2", event.userId());
        assertEquals(SLOT, event.interval());
        assertFalse(event.waitlistEntryId().isBlank());
        assertEquals(1, event.waitlistSequence());
    }

    @Test
    void promotionCarriesThePartySizeTheWaiterAskedFor() {
        bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT, 7);
        String held = service.bookingsForRoom("r-1").get(0).id();
        CancellationResult result = service.cancelBooking(held, "u-1");
        assertEquals(7, result.promotions().get(0).booking().attendees());
    }

    @Test
    void promotionSkipsAWaiterWhoIsBusyElsewhere() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);
        assertTrue(book("r-2", "u-2", SLOT).isConfirmed());

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertEquals(1, result.promotions().size());
        assertEquals("u-3", result.promotions().get(0).booking().userId());
        assertEquals(List.of("u-2"), service.waitlistForRoom("r-1").stream()
            .map(WaitlistEntry::userId).toList());
    }

    @Test
    void promotionSkipsAWaiterAtTheirBookingCap() {
        build(BookingPolicy.defaults().withMaxActiveBookingsPerUser(1));
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        assertTrue(book("r-2", "u-2", Fixtures.interval("14:00", "15:00")).isConfirmed());

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertTrue(result.promotions().isEmpty());
        assertEquals(1, waitlist.count());
    }

    @Test
    void promotionNeverCreatesAConflictingBooking() {
        String morning = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "12:00"));
        book("r-1", "u-2", Fixtures.interval("10:00", "11:00"));
        assertTrue(book("r-1", "u-3", Fixtures.interval("12:00", "13:00")).isConfirmed());

        service.cancelBooking(morning, "u-1");

        List<Booking> held = service.bookingsForRoom("r-1").stream().filter(Booking::holdsRoom).toList();
        assertEquals(2, held.size());
        assertFalse(held.get(0).interval().overlaps(held.get(1).interval()));
    }

    @Test
    void freedTimeCanPromoteMoreThanOneWaiter() {
        String longBooking = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "12:00"));
        book("r-1", "u-2", Fixtures.interval("10:00", "11:00"));
        book("r-1", "u-3", Fixtures.interval("11:00", "12:00"));

        CancellationResult result = service.cancelBooking(longBooking, "u-1");

        assertEquals(2, result.promotions().size());
        assertEquals(List.of("u-2", "u-3"),
            result.promotions().stream().map(p -> p.booking().userId()).toList());
        assertEquals(0, waitlist.count());
    }

    @Test
    void twoWaitersForTheSameSlotYieldOnePromotion() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertEquals(1, result.promotions().size());
        assertEquals(1, waitlist.count());
    }

    @Test
    void aWaiterWhoseSlotHasPassedIsDroppedRatherThanPromoted() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        clock.setTo("2026-10-19T23:00:00Z");
        String later = bookAndGetId("r-1", "u-3", Fixtures.intervalOn("2026-10-22", "10:00", "11:00"));
        clock.setTo("2026-10-22T09:00:00Z");

        CancellationResult result = service.cancelBooking(later, "u-3");

        assertTrue(result.isCancelled());
        assertTrue(result.promotions().isEmpty());
        assertEquals(0, waitlist.count());
        assertTrue(service.findBooking(held).orElseThrow().holdsRoom());
    }

    @Test
    void aWaiterInARoomTakenOutOfServiceIsDropped() {
        String held = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        addRoom(Fixtures.studyRoom("r-1", 10).withActive(false));

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertTrue(result.promotions().isEmpty());
        assertEquals(0, waitlist.count());
    }

    @Test
    void aWaiterMayLeaveTheQueue() {
        bookAndGetId("r-1", "u-1", SLOT);
        WaitlistEntry entry = book("r-1", "u-2", SLOT).waitlistEntry().orElseThrow();
        assertTrue(service.leaveWaitlist(entry.id(), "u-2").isEmpty());
        assertEquals(0, waitlist.count());
    }

    @Test
    void onlyTheWaiterMayLeaveTheirOwnQueueEntry() {
        bookAndGetId("r-1", "u-1", SLOT);
        WaitlistEntry entry = book("r-1", "u-2", SLOT).waitlistEntry().orElseThrow();
        assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER),
            service.leaveWaitlist(entry.id(), "u-3"));
        assertEquals(1, waitlist.count());
    }

    @Test
    void leavingAnUnknownQueueEntryIsRefused() {
        assertEquals(Optional.of(RejectionReason.WAITLIST_ENTRY_NOT_FOUND),
            service.leaveWaitlist("wl-missing", "u-2"));
    }

    @Test
    void aUserSeesTheirOwnQueueEntries() {
        bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        assertEquals(1, service.waitlistForUser("u-2").size());
        assertTrue(service.waitlistForUser("u-1").isEmpty());
    }
}
