package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.BookingStatus;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.events.BookingCancelledEvent;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingServiceCancelTest extends BookingServiceTestBase {

    @Test
    void ownerCancelsAnUpcomingBooking() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        CancellationResult result = service.cancelBooking(id, "u-1");
        assertTrue(result.isCancelled());
        assertEquals(BookingStatus.CANCELLED, service.findBooking(id).orElseThrow().status());
    }

    @Test
    void cancellingKeepsTheRecordAndFreesTheRoom() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        service.cancelBooking(id, "u-1");
        assertEquals(1, bookings.count());
        assertTrue(service.bookingsForRoom("r-1").stream().noneMatch(b -> b.holdsRoom()));
        assertTrue(book("r-1", "u-2", Fixtures.interval("10:00", "11:00")).isConfirmed());
    }

    @Test
    void publishesACancelledEvent() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        service.cancelBooking(id, "u-1");
        assertEquals(List.of("booking-created", "booking-cancelled"), publisher.types());
        BookingCancelledEvent event = publisher.ofType(BookingCancelledEvent.class).get(0);
        assertEquals(id, event.bookingId());
        assertEquals("u-1", event.userId());
        assertEquals(4, event.attendees());
    }

    @Test
    void rejectsAnUnknownBooking() {
        CancellationResult result = service.cancelBooking("bk-missing", "u-1");
        assertEquals(Optional.of(RejectionReason.BOOKING_NOT_FOUND), result.rejectionReason());
    }

    @Test
    void rejectsSomeoneElsesBooking() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        CancellationResult result = service.cancelBooking(id, "u-2");
        assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER), result.rejectionReason());
        assertTrue(service.findBooking(id).orElseThrow().holdsRoom());
    }

    @Test
    void rejectsASecondCancellation() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        service.cancelBooking(id, "u-1");
        CancellationResult result = service.cancelBooking(id, "u-1");
        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_CANCELLED), result.rejectionReason());
    }

    @Test
    void rejectsCancellingAfterTheBookingHasStarted() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        clock.setTo("2026-10-20T10:30:00Z");
        CancellationResult result = service.cancelBooking(id, "u-1");
        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_STARTED), result.rejectionReason());
        assertTrue(service.findBooking(id).orElseThrow().holdsRoom());
    }

    @Test
    void aRejectedCancellationPublishesNothing() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        service.cancelBooking(id, "u-2");
        assertEquals(List.of("booking-created"), publisher.types());
    }

    @Test
    void cancellingWithNothingWaitingPromotesNobody() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        CancellationResult result = service.cancelBooking(id, "u-1");
        assertTrue(result.promotions().isEmpty());
    }

    @Test
    void aFailingSubscriberDoesNotUndoTheCancellation() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        publisher.failWith(new IllegalStateException("subscriber is down"));
        CancellationResult result = service.cancelBooking(id, "u-1");
        assertTrue(result.isCancelled());
        assertFalse(service.findBooking(id).orElseThrow().holdsRoom());
    }
}
