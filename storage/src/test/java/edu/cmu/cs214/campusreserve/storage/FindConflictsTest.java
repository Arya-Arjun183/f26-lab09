package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The booking store's room-and-window query. */
class FindConflictsTest {

    private InMemoryBookingStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryBookingStore();
    }

    private void hold(String id, String roomId, String startTime, String endTime) {
        store.save(Booking.confirmed(
            id, roomId, "u-1", StorageFixtures.interval(startTime, endTime), 4, StorageFixtures.NOW));
    }

    private List<String> idsOver(String roomId, String startTime, String endTime) {
        return store.findConflicts(roomId, StorageFixtures.interval(startTime, endTime)).stream()
            .map(Booking::id)
            .toList();
    }

    @Test
    void findsABookingThatCoversTheWholeWindow() {
        hold("bk-1", "r-1", "09:00", "12:00");
        assertEquals(List.of("bk-1"), idsOver("r-1", "10:00", "11:00"));
    }

    @Test
    void findsABookingInsideTheWindow() {
        hold("bk-1", "r-1", "10:15", "10:45");
        assertEquals(List.of("bk-1"), idsOver("r-1", "10:00", "11:00"));
    }

    @Test
    void findsABookingThatOverlapsEitherEdge() {
        hold("bk-1", "r-1", "09:00", "10:15");
        hold("bk-2", "r-1", "10:45", "12:00");
        assertEquals(List.of("bk-1", "bk-2"), idsOver("r-1", "10:00", "11:00"));
    }

    @Test
    void aBookingThatEndsWhenTheWindowOpensIsNotAConflict() {
        hold("bk-1", "r-1", "09:00", "10:00");
        assertTrue(idsOver("r-1", "10:00", "11:00").isEmpty());
    }

    @Test
    void aBookingThatStartsWhenTheWindowClosesIsNotAConflict() {
        hold("bk-1", "r-1", "11:00", "12:00");
        assertTrue(idsOver("r-1", "10:00", "11:00").isEmpty());
    }

    @Test
    void oneSharedMinuteIsEnough() {
        hold("bk-1", "r-1", "09:00", "10:01");
        hold("bk-2", "r-1", "10:59", "12:00");
        assertEquals(List.of("bk-1", "bk-2"), idsOver("r-1", "10:00", "11:00"));
    }

    @Test
    void anotherRoomIsNeverAConflict() {
        hold("bk-1", "r-2", "10:00", "11:00");
        assertTrue(idsOver("r-1", "10:00", "11:00").isEmpty());
    }

    @Test
    void aCancelledBookingIsNotAConflict() {
        store.save(Booking.confirmed("bk-1", "r-1", "u-1",
            StorageFixtures.interval("10:00", "11:00"), 4, StorageFixtures.NOW).cancelled());
        assertTrue(idsOver("r-1", "10:00", "11:00").isEmpty());
    }

    @Test
    void theAnswerComesBackOrderedByStart() {
        hold("bk-1", "r-1", "14:00", "15:00");
        hold("bk-2", "r-1", "09:00", "10:00");
        hold("bk-3", "r-1", "11:00", "12:00");
        assertEquals(List.of("bk-2", "bk-3", "bk-1"), idsOver("r-1", "08:00", "16:00"));
    }

    @Test
    void anEmptyRoomHasNothingToReport() {
        assertTrue(idsOver("r-1", "10:00", "11:00").isEmpty());
        assertTrue(idsOver("r-nowhere", "10:00", "11:00").isEmpty());
    }

    @Test
    void theAnswerMatchesTheRoomsHeldBookingsOverTheSameWindow() {
        hold("bk-1", "r-1", "09:00", "10:00");
        hold("bk-2", "r-1", "10:00", "11:00");
        hold("bk-3", "r-1", "11:30", "12:30");
        TimeInterval window = StorageFixtures.interval("09:30", "11:45");

        List<Booking> found = store.findConflicts("r-1", window);

        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(booking -> booking.interval().overlaps(window)));
    }
}
