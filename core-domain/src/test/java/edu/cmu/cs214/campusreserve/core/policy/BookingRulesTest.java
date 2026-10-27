package edu.cmu.cs214.campusreserve.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import edu.cmu.cs214.campusreserve.core.support.TestClock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BookingRulesTest {

    private TestClock clock;
    private BookingRules rules;
    private Room studyRoom;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.NOW);
        rules = new BookingRules(BookingPolicy.defaults(), clock);
        studyRoom = Fixtures.studyRoom("r-1", 10);
    }

    private Optional<RejectionReason> check(TimeInterval interval, int attendees) {
        return rules.checkRequest(BookingRequest.of("r-1", "u-1", interval, attendees), studyRoom);
    }

    @Test
    void acceptsAWellFormedRequest() {
        assertTrue(check(Fixtures.interval("10:00", "11:00"), 4).isEmpty());
    }

    @Nested
    @DisplayName("interval shape")
    class Shape {

        @Test
        void rejectsBookingsShorterThanTheMinimum() {
            assertEquals(Optional.of(RejectionReason.INTERVAL_TOO_SHORT),
                check(Fixtures.interval("10:00", "10:15"), 4));
        }

        @Test
        void acceptsExactlyTheMinimum() {
            assertTrue(check(Fixtures.interval("10:00", "10:30"), 4).isEmpty());
        }

        @Test
        void rejectsBookingsLongerThanTheRoomKindAllows() {
            assertEquals(Optional.of(RejectionReason.INTERVAL_TOO_LONG),
                check(Fixtures.interval("10:00", "14:00"), 4));
        }

        @Test
        void aClassroomMayRunLongerThanAStudyRoom() {
            Room classroom = Fixtures.classroom("r-2");
            BookingRequest request = BookingRequest.of("r-2", "u-1", Fixtures.interval("10:00", "14:00"), 4);
            assertTrue(rules.checkRequest(request, classroom).isEmpty());
            assertEquals(240, rules.effectiveMaxDurationMinutes(classroom));
            assertEquals(180, rules.effectiveMaxDurationMinutes(studyRoom));
        }

        @Test
        void rejectsIntervalsOffTheSlotGrid() {
            assertEquals(Optional.of(RejectionReason.INTERVAL_NOT_ALIGNED),
                check(Fixtures.interval("10:05", "11:05"), 4));
        }

        @Test
        void rejectsIntervalsThatCrossMidnight() {
            TimeInterval overnight = TimeInterval.parse("2026-10-20T23:00:00Z", "2026-10-21T01:00:00Z");
            assertEquals(Optional.of(RejectionReason.INTERVAL_SPANS_DAYS), check(overnight, 4));
        }
    }

    @Nested
    @DisplayName("calendar window")
    class Window {

        @Test
        void rejectsIntervalsInThePast() {
            assertEquals(Optional.of(RejectionReason.INTERVAL_IN_PAST),
                check(Fixtures.intervalOn("2026-10-18", "10:00", "11:00"), 4));
        }

        @Test
        void rejectsAnIntervalStartingExactlyNow() {
            clock.setTo("2026-10-20T10:00:00Z");
            assertEquals(Optional.of(RejectionReason.INTERVAL_IN_PAST),
                check(Fixtures.interval("10:00", "11:00"), 4));
        }

        @Test
        void rejectsIntervalsBeforeOpening() {
            assertEquals(Optional.of(RejectionReason.OUTSIDE_OPENING_HOURS),
                check(Fixtures.interval("05:00", "06:00"), 4));
        }

        @Test
        void rejectsIntervalsRunningPastClosing() {
            assertEquals(Optional.of(RejectionReason.OUTSIDE_OPENING_HOURS),
                check(Fixtures.interval("21:30", "22:30"), 4));
        }

        @Test
        void acceptsAnIntervalEndingExactlyAtClosing() {
            assertTrue(check(Fixtures.interval("21:00", "22:00"), 4).isEmpty());
        }

        @Test
        void rejectsBookingsFurtherAheadThanTheRoomKindAllows() {
            assertEquals(Optional.of(RejectionReason.TOO_FAR_IN_ADVANCE),
                check(Fixtures.intervalOn("2026-12-01", "10:00", "11:00"), 4));
            assertEquals(14, rules.effectiveMaxAdvanceDays(studyRoom));
        }

        @Test
        void aClassroomMayBeBookedFurtherAhead() {
            BookingRequest request =
                BookingRequest.of("r-2", "u-1", Fixtures.intervalOn("2026-12-01", "10:00", "11:00"), 4);
            assertTrue(rules.checkRequest(request, Fixtures.classroom("r-2")).isEmpty());
        }
    }

    @Nested
    @DisplayName("room state and party size")
    class RoomState {

        @Test
        void rejectsInactiveRooms() {
            BookingRequest request = Fixtures.request("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
            assertEquals(Optional.of(RejectionReason.ROOM_INACTIVE),
                rules.checkRequest(request, studyRoom.withActive(false)));
        }

        @Test
        void rejectsPartiesLargerThanTheRoom() {
            assertEquals(Optional.of(RejectionReason.ATTENDEES_EXCEED_CAPACITY),
                check(Fixtures.interval("10:00", "11:00"), 11));
        }

        @Test
        void acceptsAPartyThatExactlyFillsTheRoom() {
            assertTrue(check(Fixtures.interval("10:00", "11:00"), 10).isEmpty());
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        private Booking booking() {
            return Booking.confirmed("bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4,
                Instant.parse(Fixtures.NOW));
        }

        @Test
        void ownerMayCancelAnUpcomingBooking() {
            assertTrue(rules.checkCancellable(booking(), "u-1").isEmpty());
        }

        @Test
        void nonOwnerMayNot() {
            assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER),
                rules.checkCancellable(booking(), "u-2"));
        }

        @Test
        void anAlreadyCancelledBookingCannotBeCancelledAgain() {
            assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_CANCELLED),
                rules.checkCancellable(booking().cancelled(), "u-1"));
        }

        @Test
        void aStartedBookingCannotBeCancelled() {
            clock.setTo("2026-10-20T10:00:00Z");
            assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_STARTED),
                rules.checkCancellable(booking(), "u-1"));
            assertFalse(rules.isUpcoming(booking()));
        }

        @Test
        void ownershipIsCheckedBeforeTiming() {
            clock.setTo("2026-10-20T10:00:00Z");
            assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER),
                rules.checkCancellable(booking(), "u-2"));
        }
    }

    @Nested
    @DisplayName("promotability")
    class Promotability {

        @Test
        void anUpcomingWellFormedIntervalIsPromotable() {
            assertTrue(rules.isPromotable(Fixtures.interval("10:00", "11:00"), studyRoom));
        }

        @Test
        void aPassedIntervalIsNotPromotable() {
            clock.setTo("2026-10-20T12:00:00Z");
            assertFalse(rules.isPromotable(Fixtures.interval("10:00", "11:00"), studyRoom));
        }

        @Test
        void anIntervalInAnInactiveRoomIsNotPromotable() {
            assertFalse(rules.isPromotable(Fixtures.interval("10:00", "11:00"), studyRoom.withActive(false)));
        }
    }
}
