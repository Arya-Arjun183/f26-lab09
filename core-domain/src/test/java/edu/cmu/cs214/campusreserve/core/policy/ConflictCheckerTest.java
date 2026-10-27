package edu.cmu.cs214.campusreserve.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflictCheckerTest {

    private static final Instant CREATED = Instant.parse(Fixtures.NOW);

    private final ConflictChecker checker = new ConflictChecker();

    private static Booking booking(String id, String start, String end) {
        return Booking.confirmed(id, "r-1", "u-1", Fixtures.interval(start, end), 4, CREATED);
    }

    @Test
    void anEmptyCalendarHasNoConflicts() {
        assertFalse(checker.hasConflict(List.of(), Fixtures.interval("10:00", "11:00")));
        assertTrue(checker.firstConflict(List.of(), Fixtures.interval("10:00", "11:00")).isEmpty());
    }

    @Test
    void findsAnOverlappingBooking() {
        List<Booking> existing = List.of(booking("bk-1", "10:00", "11:00"));
        assertTrue(checker.hasConflict(existing, Fixtures.interval("10:30", "11:30")));
        assertEquals("bk-1", checker.firstConflict(existing, Fixtures.interval("10:30", "11:30"))
            .orElseThrow().id());
    }

    @Test
    void backToBackBookingsDoNotConflict() {
        List<Booking> existing = List.of(booking("bk-1", "10:00", "11:00"));
        assertFalse(checker.hasConflict(existing, Fixtures.interval("11:00", "12:00")));
        assertFalse(checker.hasConflict(existing, Fixtures.interval("09:00", "10:00")));
    }

    @Test
    void cancelledBookingsDoNotHoldTheRoom() {
        List<Booking> existing = List.of(booking("bk-1", "10:00", "11:00").cancelled());
        assertFalse(checker.hasConflict(existing, Fixtures.interval("10:00", "11:00")));
    }

    @Test
    void reportsTheEarliestConflictFirst() {
        List<Booking> existing = List.of(
            booking("bk-late", "11:00", "12:00"),
            booking("bk-early", "09:00", "10:30"));
        assertEquals("bk-early", checker.firstConflict(existing, Fixtures.interval("09:30", "11:30"))
            .orElseThrow().id());
    }

    @Test
    void listsEveryConflictInStartOrder() {
        List<Booking> existing = List.of(
            booking("bk-3", "12:00", "13:00"),
            booking("bk-1", "09:00", "10:00"),
            booking("bk-2", "10:30", "11:30"),
            booking("bk-4", "15:00", "16:00").cancelled());
        List<Booking> conflicts = checker.conflicts(existing, Fixtures.interval("09:30", "12:30"));
        assertEquals(List.of("bk-1", "bk-2", "bk-3"), conflicts.stream().map(Booking::id).toList());
    }

    @Test
    void detectsAPairOfBookingsThatCollideWithEachOther() {
        assertTrue(checker.containsMutualConflict(List.of(
            booking("bk-1", "10:00", "11:00"),
            booking("bk-2", "10:30", "11:30"))));
        assertFalse(checker.containsMutualConflict(List.of(
            booking("bk-1", "10:00", "11:00"),
            booking("bk-2", "11:00", "12:00"))));
        assertFalse(checker.containsMutualConflict(List.of()));
    }

    @Test
    void mutualConflictIgnoresCancelledBookings() {
        assertFalse(checker.containsMutualConflict(List.of(
            booking("bk-1", "10:00", "11:00"),
            booking("bk-2", "10:30", "11:30").cancelled())));
    }

    @Test
    void aCandidateInsideAnExistingBookingConflicts() {
        List<Booking> existing = List.of(booking("bk-1", "09:00", "13:00"));
        TimeInterval candidate = Fixtures.interval("10:00", "11:00");
        assertTrue(checker.hasConflict(existing, candidate));
    }
}
