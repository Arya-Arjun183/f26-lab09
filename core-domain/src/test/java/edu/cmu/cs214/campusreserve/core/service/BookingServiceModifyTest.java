package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingModification;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.ModificationResult;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.events.BookingModifiedEvent;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingServiceModifyTest extends BookingServiceTestBase {

    private static final TimeInterval SLOT = Fixtures.interval("10:00", "11:00");

    @Test
    void theOwnerMovesAnUpcomingBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        TimeInterval moved = Fixtures.interval("13:00", "14:00");

        ModificationResult result = service.modifyBooking(id, "u-1", moved, 4);

        assertTrue(result.isModified());
        assertEquals(moved, result.modifiedBooking().orElseThrow().interval());
        assertEquals(moved, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void theOwnerChangesOnlyThePartySize() {
        String id = bookAndGetId("r-1", "u-1", SLOT);

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 6);

        Booking stored = service.findBooking(id).orElseThrow();
        assertEquals(SLOT, stored.interval());
        assertEquals(6, stored.attendees());
        assertTrue(result.promotions().isEmpty());
    }

    @Test
    void nudgingABookingByOneSlotIsAllowed() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        TimeInterval nudged = Fixtures.interval("10:15", "11:15");

        ModificationResult result = service.modifyBooking(id, "u-1", nudged, 4);

        assertTrue(result.isModified());
        assertEquals(nudged, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void theHistoryKeepsWhatTheBookingUsedToBe() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        TimeInterval moved = Fixtures.interval("13:00", "14:00");
        service.modifyBooking(id, "u-1", moved, 4);
        service.modifyBooking(id, "u-1", moved, 2);

        List<BookingModification> history = service.findBooking(id).orElseThrow().modifications();
        assertEquals(2, history.size());
        assertEquals(SLOT, history.get(0).previousInterval());
        assertEquals(moved, history.get(0).interval());
        assertTrue(history.get(0).movedInTime());
        assertFalse(history.get(0).resized());
        assertEquals(4, history.get(1).previousAttendees());
        assertEquals(2, history.get(1).attendees());
        assertTrue(history.get(1).resized());
        assertFalse(history.get(1).movedInTime());
    }

    @Test
    void publishesAModifiedEventCarryingBothIntervals() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        TimeInterval moved = Fixtures.interval("13:00", "14:00");
        service.modifyBooking(id, "u-1", moved, 6);

        assertEquals(List.of("booking-created", "booking-modified"), publisher.types());
        BookingModifiedEvent event = publisher.ofType(BookingModifiedEvent.class).get(0);
        assertEquals(id, event.bookingId());
        assertEquals("r-1", event.roomId());
        assertEquals(moved, event.interval());
        assertEquals(SLOT, event.previousInterval());
        assertEquals(6, event.attendees());
        assertEquals(4, event.previousAttendees());
    }

    @Test
    void aChangeThatChangesNothingLeavesNoTrace() {
        String id = bookAndGetId("r-1", "u-1", SLOT);

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 4);

        assertTrue(result.isModified());
        assertTrue(service.findBooking(id).orElseThrow().modifications().isEmpty());
        assertEquals(List.of("booking-created"), publisher.types());
    }

    @Test
    void rejectsAnUnknownBooking() {
        ModificationResult result = service.modifyBooking("bk-missing", "u-1", SLOT, 4);
        assertEquals(Optional.of(RejectionReason.BOOKING_NOT_FOUND), result.rejectionReason());
    }

    @Test
    void rejectsSomeoneElsesBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        ModificationResult result =
            service.modifyBooking(id, "u-2", Fixtures.interval("13:00", "14:00"), 4);
        assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER), result.rejectionReason());
        assertEquals(SLOT, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void rejectsABookingThatHasAlreadyStarted() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        clock.setTo("2026-10-20T10:30:00Z");

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_STARTED), result.rejectionReason());
    }

    @Test
    void rejectsACancelledBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        service.cancelBooking(id, "u-1");

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_CANCELLED), result.rejectionReason());
    }

    @Test
    void aRejectedModificationLeavesTheBookingExactlyAsItWas() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        bookAndGetId("r-1", "u-2", Fixtures.interval("13:00", "14:00"));

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertEquals(Optional.of(RejectionReason.ROOM_UNAVAILABLE), result.rejectionReason());
        Booking stored = service.findBooking(id).orElseThrow();
        assertEquals(SLOT, stored.interval());
        assertEquals(4, stored.attendees());
        assertTrue(stored.modifications().isEmpty());
        assertEquals(List.of("booking-created", "booking-created"), publisher.types());
    }

    @Test
    void rejectsANewIntervalTheBookingRulesRefuse() {
        String id = bookAndGetId("r-1", "u-1", SLOT);

        assertEquals(Optional.of(RejectionReason.OUTSIDE_OPENING_HOURS),
            service.modifyBooking(id, "u-1", Fixtures.interval("05:00", "06:00"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_NOT_ALIGNED),
            service.modifyBooking(id, "u-1", Fixtures.interval("10:05", "11:05"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_TOO_SHORT),
            service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "10:15"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_IN_PAST),
            service.modifyBooking(id, "u-1",
                Fixtures.intervalOn("2026-10-18", "10:00", "11:00"), 4).rejectionReason());
    }

    @Test
    void rejectsAPartySizeTheRoomCannotSeat() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 40);
        assertEquals(Optional.of(RejectionReason.ATTENDEES_EXCEED_CAPACITY), result.rejectionReason());
    }

    @Test
    void rejectsAMoveOntoTheBookersOwnOtherBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        bookAndGetId("r-2", "u-1", Fixtures.interval("13:00", "14:00"));

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:30", "14:30"), 4);

        assertEquals(Optional.of(RejectionReason.USER_DOUBLE_BOOKED), result.rejectionReason());
    }

    @Test
    void theBookingCapDoesNotApplyToAChange() {
        build(BookingPolicy.defaults().withMaxActiveBookingsPerUser(1));
        String id = bookAndGetId("r-1", "u-1", SLOT);

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertTrue(result.isModified());
    }

    @Test
    void freedTimeGoesToTheWaitlistInArrivalOrder() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        List<CancellationResult.Promotion> promotions = result.promotions();
        assertEquals(1, promotions.size());
        assertEquals("u-2", promotions.get(0).booking().userId());
        assertEquals(SLOT, promotions.get(0).booking().interval());
        assertEquals(1, waitlist.count());
    }

    @Test
    void shorteningABookingHandsTheTailToSomebodyWaiting() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", Fixtures.interval("10:30", "11:00"));

        service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "10:30"), 4);

        assertEquals(0, waitlist.count());
        assertTrue(service.activeBookingsForUser("u-2").stream()
            .anyMatch(booking -> booking.interval().equals(Fixtures.interval("10:30", "11:00"))));
    }

    @Test
    void lengtheningABookingPromotesNobody() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);

        ModificationResult result =
            service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "12:00"), 4);

        assertTrue(result.isModified());
        assertTrue(result.promotions().isEmpty());
        assertEquals(1, waitlist.count());
    }

    @Test
    void aPartySizeChangeStartsNoPromotion() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 2);

        assertTrue(result.promotions().isEmpty());
        assertEquals(1, waitlist.count());
    }

    @Test
    void aPromotionOffAModificationGetsItsOwnEvent() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);

        service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertEquals(List.of("booking-created", "booking-modified", "waitlist-promoted"),
            publisher.types());
    }

    @Test
    void theStoredBookingIsSavedBeforeTheWaitlistIsWalked() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);

        service.modifyBooking(id, "u-1", Fixtures.interval("13:00", "14:00"), 4);

        assertEquals(2, service.bookingsForRoom("r-1").size());
        assertFalse(service.bookingsForRoom("r-1").stream()
            .anyMatch(booking -> booking.userId().equals("u-1") && booking.interval().equals(SLOT)));
    }
}
