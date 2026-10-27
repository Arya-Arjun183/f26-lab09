package edu.cmu.cs214.campusreserve.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingModificationTest {

    private static final Instant NOW = Instant.parse(Fixtures.NOW);
    private static final TimeInterval MORNING = Fixtures.interval("10:00", "11:00");
    private static final TimeInterval AFTERNOON = Fixtures.interval("13:00", "14:00");

    @Test
    void aFreshBookingHasNoHistory() {
        Booking booking = Booking.confirmed("bk-1", "r-1", "u-1", MORNING, 4, NOW);
        assertTrue(booking.modifications().isEmpty());
        assertTrue(booking.lastModification().isEmpty());
    }

    @Test
    void aChangeAppendsToTheHistoryAndLeavesTheOriginalAlone() {
        Booking booking = Booking.confirmed("bk-1", "r-1", "u-1", MORNING, 4, NOW);
        Booking moved = booking.modifiedTo(AFTERNOON, 6, NOW);

        assertEquals(AFTERNOON, moved.interval());
        assertEquals(6, moved.attendees());
        assertEquals(1, moved.modifications().size());
        assertEquals(MORNING, moved.lastModification().orElseThrow().previousInterval());
        assertTrue(booking.modifications().isEmpty());
        assertEquals(MORNING, booking.interval());
    }

    @Test
    void cancellingKeepsTheHistory() {
        Booking moved = Booking.confirmed("bk-1", "r-1", "u-1", MORNING, 4, NOW)
            .modifiedTo(AFTERNOON, 4, NOW);
        assertEquals(1, moved.cancelled().modifications().size());
        assertEquals(BookingStatus.CANCELLED, moved.cancelled().status());
    }

    @Test
    void aChangeReportsWhichPartMoved() {
        BookingModification moved = new BookingModification(NOW, MORNING, 4, AFTERNOON, 4);
        assertTrue(moved.movedInTime());
        assertFalse(moved.resized());

        BookingModification resized = new BookingModification(NOW, MORNING, 4, MORNING, 6);
        assertFalse(resized.movedInTime());
        assertTrue(resized.resized());
    }

    @Test
    void aChangeHasToChangeSomething() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingModification(NOW, MORNING, 4, MORNING, 4));
        assertThrows(IllegalArgumentException.class,
            () -> Booking.confirmed("bk-1", "r-1", "u-1", MORNING, 4, NOW).modifiedTo(MORNING, 4, NOW));
    }

    @Test
    void aChangeNeedsAPositivePartySize() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingModification(NOW, MORNING, 4, AFTERNOON, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new BookingModification(NOW, MORNING, 0, AFTERNOON, 4));
    }
}
