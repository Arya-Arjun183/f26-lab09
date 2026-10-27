package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import edu.cmu.cs214.campusreserve.core.support.InMemoryStores;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityServiceTest {

    private static final LocalDate DAY = LocalDate.parse(Fixtures.DAY);
    private static final Instant CREATED = Instant.parse(Fixtures.NOW);

    private InMemoryStores.Bookings bookings;
    private AvailabilityService availability;

    @BeforeEach
    void setUp() {
        bookings = new InMemoryStores.Bookings();
        availability = new AvailabilityService(bookings, BookingPolicy.defaults());
    }

    private void hold(String id, String start, String end) {
        bookings.save(Booking.confirmed(id, "r-1", "u-1", Fixtures.interval(start, end), 4, CREATED));
    }

    private static List<String> render(List<TimeInterval> intervals) {
        return intervals.stream().map(TimeInterval::toString).toList();
    }

    @Test
    void anEmptyRoomIsFreeForTheWholeWindow() {
        TimeInterval window = Fixtures.interval("09:00", "17:00");
        assertEquals(List.of(window), availability.freeIntervals("r-1", window));
        assertTrue(availability.isFree("r-1", window));
    }

    @Test
    void aFullyBookedWindowHasNoFreeTime() {
        hold("bk-1", "09:00", "17:00");
        assertTrue(availability.freeIntervals("r-1", Fixtures.interval("09:00", "17:00")).isEmpty());
        assertFalse(availability.isFree("r-1", Fixtures.interval("10:00", "11:00")));
    }

    @Test
    void reportsGapsBetweenBookings() {
        hold("bk-1", "09:00", "10:00");
        hold("bk-2", "12:00", "13:00");
        List<TimeInterval> free = availability.freeIntervals("r-1", Fixtures.interval("08:00", "14:00"));
        assertEquals(render(List.of(
            Fixtures.interval("08:00", "09:00"),
            Fixtures.interval("10:00", "12:00"),
            Fixtures.interval("13:00", "14:00"))), render(free));
    }

    @Test
    void clipsBookingsThatRunPastTheWindow() {
        hold("bk-1", "08:00", "11:00");
        List<TimeInterval> free = availability.freeIntervals("r-1", Fixtures.interval("09:00", "12:00"));
        assertEquals(render(List.of(Fixtures.interval("11:00", "12:00"))), render(free));
    }

    @Test
    void ignoresCancelledBookings() {
        bookings.save(Booking.confirmed("bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, CREATED)
            .cancelled());
        TimeInterval window = Fixtures.interval("09:00", "12:00");
        assertEquals(List.of(window), availability.freeIntervals("r-1", window));
    }

    @Test
    void backToBackBookingsLeaveNoGap() {
        hold("bk-1", "09:00", "10:00");
        hold("bk-2", "10:00", "11:00");
        List<TimeInterval> free = availability.freeIntervals("r-1", Fixtures.interval("09:00", "12:00"));
        assertEquals(render(List.of(Fixtures.interval("11:00", "12:00"))), render(free));
    }

    @Test
    void nestedBookingsDoNotProduceNegativeGaps() {
        hold("bk-1", "09:00", "13:00");
        hold("bk-2", "10:00", "11:00");
        List<TimeInterval> free = availability.freeIntervals("r-1", Fixtures.interval("09:00", "14:00"));
        assertEquals(render(List.of(Fixtures.interval("13:00", "14:00"))), render(free));
    }

    @Test
    void listsBusyIntervalsInStartOrder() {
        hold("bk-2", "12:00", "13:00");
        hold("bk-1", "09:00", "10:00");
        assertEquals(render(List.of(Fixtures.interval("09:00", "10:00"), Fixtures.interval("12:00", "13:00"))),
            render(availability.busyIntervals("r-1", Fixtures.interval("08:00", "14:00"))));
    }

    @Test
    void theOpeningWindowComesFromThePolicy() {
        assertEquals(Fixtures.interval("06:00", "22:00"), availability.openingWindow(DAY));
    }

    @Test
    void reportsFreeTimeOnADateWithinOpeningHours() {
        hold("bk-1", "06:00", "20:00");
        assertEquals(render(List.of(Fixtures.interval("20:00", "22:00"))),
            render(availability.freeOnDate("r-1", DAY)));
    }

    @Test
    void listsOpenSlotsOnTheGrid() {
        hold("bk-1", "06:00", "21:00");
        List<TimeInterval> slots = availability.openSlotsOnDate("r-1", DAY, 60);
        assertEquals(render(List.of(Fixtures.interval("21:00", "22:00"))), render(slots));
    }

    @Test
    void listsEveryStartOffsetThatFits() {
        hold("bk-1", "06:00", "20:30");
        List<TimeInterval> slots = availability.openSlotsOnDate("r-1", DAY, 60);
        assertEquals(render(List.of(
            Fixtures.interval("20:30", "21:30"),
            Fixtures.interval("20:45", "21:45"),
            Fixtures.interval("21:00", "22:00"))), render(slots));
    }

    @Test
    void rejectsANonPositiveSlotLength() {
        assertThrows(IllegalArgumentException.class, () -> availability.openSlotsOnDate("r-1", DAY, 0));
    }

    @Test
    void countsBookedMinutesAgainstOpeningHours() {
        hold("bk-1", "09:00", "10:00");
        hold("bk-2", "12:00", "13:30");
        assertEquals(150, availability.bookedMinutesOnDate("r-1", DAY));
        assertEquals(150.0 / (16 * 60), availability.utilizationOnDate("r-1", DAY), 0.0001);
    }

    @Test
    void utilizationIsZeroForAnEmptyDay() {
        assertEquals(0.0, availability.utilizationOnDate("r-1", DAY), 0.0001);
    }

    @Test
    void ranksRoomsByBookedMinutes() {
        hold("bk-1", "09:00", "10:00");
        bookings.save(Booking.confirmed("bk-2", "r-2", "u-1", Fixtures.interval("09:00", "13:00"), 4, CREATED));
        List<AvailabilityService.RoomLoad> load = availability.busiestRooms(List.of("r-1", "r-2", "r-3"), DAY);
        assertEquals(List.of("r-2", "r-1", "r-3"), load.stream()
            .map(AvailabilityService.RoomLoad::roomId).toList());
        assertEquals(240, load.get(0).bookedMinutes());
        assertEquals(0, load.get(2).bookedMinutes());
    }
}
