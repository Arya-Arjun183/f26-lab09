package edu.cmu.cs214.campusreserve.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EntityTest {

    private static final Instant CREATED = Instant.parse("2026-10-19T09:00:00Z");

    @Nested
    @DisplayName("room")
    class Rooms {

        @Test
        void rejectsFieldsItCannotStore() {
            assertThrows(IllegalArgumentException.class,
                () -> Room.active("  ", "Study", "Sorrells", 8, RoomKind.STUDY_ROOM));
            assertThrows(IllegalArgumentException.class,
                () -> Room.active("r-1", " ", "Sorrells", 8, RoomKind.STUDY_ROOM));
            assertThrows(IllegalArgumentException.class,
                () -> Room.active("r 1", "Study", "Sorrells", 8, RoomKind.STUDY_ROOM));
            assertThrows(IllegalArgumentException.class,
                () -> Room.active("r-1", "Study", "Sorrells", 0, RoomKind.STUDY_ROOM));
        }

        @Test
        void trimsIdentifiersAndText() {
            Room room = Room.active(" r-1 ", " Study ", " Sorrells ", 8, RoomKind.STUDY_ROOM);
            assertEquals("r-1", room.id());
            assertEquals("Study", room.name());
            assertEquals("Sorrells", room.building());
        }

        @Test
        void fitsChecksCapacity() {
            Room room = Fixtures.studyRoom("r-1", 6);
            assertTrue(room.fits(6));
            assertFalse(room.fits(7));
            assertFalse(room.fits(0));
        }

        @Test
        void withActiveFlipsTheFlag() {
            Room room = Fixtures.studyRoom("r-1");
            assertTrue(room.active());
            assertFalse(room.withActive(false).active());
            assertEquals(room.id(), room.withActive(false).id());
        }

        @Test
        void labelNamesTheKind() {
            assertEquals("Study r-1 (Study room)", Fixtures.studyRoom("r-1").label());
        }
    }

    @Nested
    @DisplayName("room kind")
    class Kinds {

        @Test
        void carriesItsOwnLimits() {
            assertEquals(180, RoomKind.STUDY_ROOM.maxDurationMinutes());
            assertEquals(14, RoomKind.STUDY_ROOM.maxAdvanceDays());
            assertEquals(480, RoomKind.AUDITORIUM.maxDurationMinutes());
        }

        @Test
        void parsesNamesLoosely() {
            assertEquals(RoomKind.STUDY_ROOM, RoomKind.fromText("study_room"));
            assertEquals(RoomKind.STUDY_ROOM, RoomKind.fromText(" Study-Room "));
            assertEquals(RoomKind.CLASSROOM, RoomKind.fromText("classroom"));
        }

        @Test
        void rejectsUnknownNames() {
            assertThrows(IllegalArgumentException.class, () -> RoomKind.fromText("broom-closet"));
            assertThrows(IllegalArgumentException.class, () -> RoomKind.fromText(null));
        }
    }

    @Nested
    @DisplayName("user")
    class Users {

        @Test
        void requiresAWellFormedAddress() {
            assertThrows(IllegalArgumentException.class, () -> new User("u-1", "Ann", "ann-at-example.edu"));
            assertThrows(IllegalArgumentException.class, () -> new User("u-1", "Ann", "ann@@example.edu"));
            assertThrows(IllegalArgumentException.class, () -> new User("u-1", "Ann", "@example.edu"));
            assertThrows(IllegalArgumentException.class, () -> new User("u-1", "Ann", "ann@example"));
        }

        @Test
        void exposesTheLocalPart() {
            assertEquals("ann", new User("u-1", "Ann", "ann@andrew.cmu.edu").localPart());
        }
    }

    @Nested
    @DisplayName("booking")
    class Bookings {

        @Test
        void startsConfirmedAndHoldsTheRoom() {
            Booking booking = Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED);
            assertEquals(BookingStatus.CONFIRMED, booking.status());
            assertTrue(booking.holdsRoom());
        }

        @Test
        void cancellingKeepsEverythingButTheStatus() {
            Booking booking = Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED);
            Booking cancelled = booking.cancelled();
            assertEquals(BookingStatus.CANCELLED, cancelled.status());
            assertFalse(cancelled.holdsRoom());
            assertEquals(booking.interval(), cancelled.interval());
            assertEquals(booking.userId(), cancelled.userId());
            assertEquals(booking.createdAt(), cancelled.createdAt());
        }

        @Test
        void reportsOwnership() {
            Booking booking = Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED);
            assertTrue(booking.isOwnedBy("u-1"));
            assertFalse(booking.isOwnedBy("u-2"));
        }

        @Test
        void reportsWhetherItHasStarted() {
            Booking booking = Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED);
            assertTrue(booking.hasStartedBy(Instant.parse("2026-10-20T10:00:00Z")));
            assertFalse(booking.hasStartedBy(Instant.parse("2026-10-20T09:59:00Z")));
            assertEquals(60, booking.durationMinutes());
        }

        @Test
        void rejectsNonPositiveAttendees() {
            assertThrows(IllegalArgumentException.class, () -> Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 0, CREATED));
        }
    }

    @Nested
    @DisplayName("waitlist entry")
    class Waitlist {

        @Test
        void matchesARequestOnRoomUserAndInterval() {
            TimeInterval interval = Fixtures.interval("10:00", "11:00");
            WaitlistEntry entry = new WaitlistEntry("wl-1", "r-1", "u-1", interval, 4, 1, CREATED);
            assertTrue(entry.matches(Fixtures.request("r-1", "u-1", interval)));
            assertFalse(entry.matches(Fixtures.request("r-2", "u-1", interval)));
            assertFalse(entry.matches(Fixtures.request("r-1", "u-2", interval)));
            assertFalse(entry.matches(Fixtures.request("r-1", "u-1", Fixtures.interval("11:00", "12:00"))));
        }

        @Test
        void rejectsNegativeSequence() {
            TimeInterval interval = Fixtures.interval("10:00", "11:00");
            assertThrows(IllegalArgumentException.class,
                () -> new WaitlistEntry("wl-1", "r-1", "u-1", interval, 4, -1, CREATED));
        }

        @Test
        void rejectsANonPositiveAttendeeCount() {
            TimeInterval interval = Fixtures.interval("10:00", "11:00");
            assertThrows(IllegalArgumentException.class,
                () -> new WaitlistEntry("wl-1", "r-1", "u-1", interval, 0, 1, CREATED));
            assertThrows(IllegalArgumentException.class,
                () -> new WaitlistEntry("wl-1", "r-1", "u-1", interval, -1, 1, CREATED));
        }

        @Test
        void reportsWhetherTheSlotIsStillAhead() {
            WaitlistEntry entry = new WaitlistEntry(
                "wl-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, 1, CREATED);
            assertTrue(entry.isUpcomingAt(Instant.parse("2026-10-20T09:00:00Z")));
            assertFalse(entry.isUpcomingAt(Instant.parse("2026-10-20T10:00:00Z")));
        }
    }

    @Nested
    @DisplayName("outcomes")
    class Outcomes {

        @Test
        void eachOutcomeCarriesOnlyItsOwnPayload() {
            Booking booking = Booking.confirmed(
                "bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED);
            WaitlistEntry entry = new WaitlistEntry(
                "wl-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, 1, CREATED);

            BookingOutcome confirmed = new BookingOutcome.Confirmed(booking);
            assertTrue(confirmed.isConfirmed());
            assertFalse(confirmed.isRejected());
            assertEquals(booking, confirmed.confirmedBooking().orElseThrow());
            assertTrue(confirmed.rejectionReason().isEmpty());

            BookingOutcome waitlisted = new BookingOutcome.Waitlisted(entry);
            assertTrue(waitlisted.isWaitlisted());
            assertEquals(entry, waitlisted.waitlistEntry().orElseThrow());
            assertTrue(waitlisted.confirmedBooking().isEmpty());
        }

        @Test
        void aRejectionFallsBackToTheReasonMessageAndHasNoPromotions() {
            BookingOutcome outcome = BookingOutcome.rejected(RejectionReason.ROOM_NOT_FOUND);
            assertTrue(outcome.isRejected());
            assertEquals(RejectionReason.ROOM_NOT_FOUND, outcome.rejectionReason().orElseThrow());
            assertEquals(RejectionReason.ROOM_NOT_FOUND.message(),
                ((BookingOutcome.Rejected) outcome).detail());

            CancellationResult result = CancellationResult.rejected(RejectionReason.NOT_BOOKING_OWNER);
            assertFalse(result.isCancelled());
            assertTrue(result.promotions().isEmpty());
            assertTrue(result.cancelledBooking().isEmpty());
            assertEquals(RejectionReason.NOT_BOOKING_OWNER, result.rejectionReason().orElseThrow());
        }

        @Test
        void reasonCodesAreStableAndClassified() {
            assertEquals("room-not-found", RejectionReason.ROOM_NOT_FOUND.code());
            assertTrue(RejectionReason.ROOM_NOT_FOUND.isMissingRecord());
            assertTrue(RejectionReason.NOT_BOOKING_OWNER.isOwnershipProblem());
            assertFalse(RejectionReason.ROOM_UNAVAILABLE.isMissingRecord());
        }
    }
}
