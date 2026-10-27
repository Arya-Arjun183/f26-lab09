package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.events.BookingCreatedEvent;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingServiceCreateTest extends BookingServiceTestBase {

    private Optional<RejectionReason> reasonOf(BookingOutcome outcome) {
        return outcome.rejectionReason();
    }

    @Test
    void confirmsABookingInAnEmptyRoom() {
        BookingOutcome outcome = book("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        assertTrue(outcome.isConfirmed());
        Booking booking = outcome.confirmedBooking().orElseThrow();
        assertEquals("r-1", booking.roomId());
        assertEquals("u-1", booking.userId());
        assertEquals(4, booking.attendees());
        assertTrue(booking.holdsRoom());
    }

    @Test
    void storesTheConfirmedBooking() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        assertEquals(1, bookings.count());
        assertTrue(service.findBooking(id).isPresent());
        assertEquals(List.of(id), service.bookingsForRoom("r-1").stream().map(Booking::id).toList());
    }

    @Test
    void publishesACreatedEvent() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        assertEquals(List.of("booking-created"), publisher.types());
        BookingCreatedEvent event = publisher.ofType(BookingCreatedEvent.class).get(0);
        assertEquals(id, event.bookingId());
        assertEquals("r-1", event.roomId());
        assertEquals(Fixtures.interval("10:00", "11:00"), event.interval());
    }

    @Test
    void rejectsAnUnknownRoom() {
        BookingOutcome outcome = book("r-missing", "u-1", Fixtures.interval("10:00", "11:00"));
        assertEquals(Optional.of(RejectionReason.ROOM_NOT_FOUND), reasonOf(outcome));
        assertEquals(0, bookings.count());
        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void rejectsAnUnknownUser() {
        BookingOutcome outcome = book("r-1", "u-missing", Fixtures.interval("10:00", "11:00"));
        assertEquals(Optional.of(RejectionReason.USER_NOT_FOUND), reasonOf(outcome));
    }

    @Test
    void rejectsAnInactiveRoom() {
        addRoom(Fixtures.studyRoom("r-1", 10).withActive(false));
        assertEquals(Optional.of(RejectionReason.ROOM_INACTIVE),
            reasonOf(book("r-1", "u-1", Fixtures.interval("10:00", "11:00"))));
    }

    @Test
    void rejectsARequestThatBreaksTheRules() {
        assertEquals(Optional.of(RejectionReason.INTERVAL_TOO_SHORT),
            reasonOf(book("r-1", "u-1", Fixtures.interval("10:00", "10:15"))));
        assertEquals(Optional.of(RejectionReason.ATTENDEES_EXCEED_CAPACITY),
            reasonOf(book("r-1", "u-1", Fixtures.interval("10:00", "11:00"), 40)));
    }

    @Test
    void allowsBackToBackBookingsInTheSameRoom() {
        assertTrue(book("r-1", "u-1", Fixtures.interval("10:00", "11:00")).isConfirmed());
        assertTrue(book("r-1", "u-2", Fixtures.interval("11:00", "12:00")).isConfirmed());
        assertEquals(2, bookings.count());
    }

    @Test
    void allowsTheSameSlotInDifferentRooms() {
        assertTrue(book("r-1", "u-1", Fixtures.interval("10:00", "11:00")).isConfirmed());
        assertTrue(book("r-2", "u-2", Fixtures.interval("10:00", "11:00")).isConfirmed());
    }

    @Test
    void refusesToLetOneUserHoldTwoRoomsAtOnce() {
        assertTrue(book("r-1", "u-1", Fixtures.interval("10:00", "11:00")).isConfirmed());
        assertEquals(Optional.of(RejectionReason.USER_DOUBLE_BOOKED),
            reasonOf(book("r-2", "u-1", Fixtures.interval("10:30", "11:30"))));
    }

    @Test
    void aCancelledBookingDoesNotCountAgainstTheUsersCalendar() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        service.cancelBooking(id, "u-1");
        assertTrue(book("r-2", "u-1", Fixtures.interval("10:30", "11:30")).isConfirmed());
    }

    @Test
    void enforcesThePerUserBookingCap() {
        build(BookingPolicy.defaults().withMaxActiveBookingsPerUser(2));
        assertTrue(book("r-1", "u-1", Fixtures.interval("08:00", "09:00")).isConfirmed());
        assertTrue(book("r-1", "u-1", Fixtures.interval("09:00", "10:00")).isConfirmed());
        assertEquals(Optional.of(RejectionReason.USER_BOOKING_LIMIT_REACHED),
            reasonOf(book("r-1", "u-1", Fixtures.interval("10:00", "11:00"))));
    }

    @Test
    void theCapCountsOnlyBookingsTheUserStillHolds() {
        build(BookingPolicy.defaults().withMaxActiveBookingsPerUser(1));
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("08:00", "09:00"));
        service.cancelBooking(id, "u-1");
        assertTrue(book("r-1", "u-1", Fixtures.interval("10:00", "11:00")).isConfirmed());
    }

    @Test
    void aFailingSubscriberDoesNotUndoTheBooking() {
        publisher.failWith(new IllegalStateException("subscriber is down"));
        BookingOutcome outcome = book("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        assertTrue(outcome.isConfirmed());
        assertEquals(1, bookings.count());
    }

    @Test
    void listsBookingsPerUserAndPerRoom() {
        bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        bookAndGetId("r-2", "u-2", Fixtures.interval("10:00", "11:00"));
        assertEquals(1, service.bookingsForUser("u-1").size());
        assertEquals(1, service.bookingsForRoom("r-2").size());
        assertEquals(1, service.activeBookingsForUser("u-2").size());
        assertTrue(service.bookingsForUser("u-3").isEmpty());
    }

    @Test
    void reportsThePolicyItEnforces() {
        assertEquals(BookingPolicy.defaults(), service.policy());
        assertFalse(service.policy().withWaitlistEnabled(false).waitlistEnabled());
    }
}
