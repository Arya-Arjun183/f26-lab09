package edu.cmu.cs214.campusreserve.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BookingPolicyTest {

    @Test
    void defaultsAreSelfConsistent() {
        BookingPolicy policy = BookingPolicy.defaults();
        assertEquals(30, policy.minDurationMinutes());
        assertEquals(240, policy.maxDurationMinutes());
        assertEquals(15, policy.slotGranularityMinutes());
        assertEquals(6 * 60, policy.openingMinuteOfDayUtc());
        assertEquals(22 * 60, policy.closingMinuteOfDayUtc());
        assertTrue(policy.waitlistEnabled());
        assertEquals(16 * 60, policy.openMinutesPerDay());
    }

    @Test
    void rejectsMinimumLongerThanMaximum() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(300, 240, 15, 60, 360, 1320, 5, true));
    }

    @Test
    void acceptsAMinimumEqualToTheMaximum() {
        BookingPolicy fixedLength = new BookingPolicy(240, 240, 15, 60, 360, 1320, 5, true);
        assertEquals(240, fixedLength.minDurationMinutes());
        assertEquals(240, fixedLength.maxDurationMinutes());
    }

    @Test
    void rejectsMinimumThatIsNotAWholeNumberOfSlots() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(20, 240, 15, 60, 360, 1320, 5, true));
    }

    @Test
    void rejectsOpeningAfterClosing() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 15, 60, 1320, 360, 5, true));
    }

    @Test
    void rejectsMinutesOutsideTheDay() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 15, 60, -1, 1320, 5, true));
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 15, 60, 360, 2000, 5, true));
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 15, 60, 360, 1441, 5, true));
    }

    @Test
    void acceptsAClosingTimeOfExactlyMidnight() {
        BookingPolicy allDay = new BookingPolicy(30, 240, 15, 60, 360, 1440, 5, true);
        assertEquals(1440, allDay.closingMinuteOfDayUtc());
        assertEquals(1080, allDay.openMinutesPerDay());
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 15, 60, 360, 1320, 0, true));
        assertThrows(IllegalArgumentException.class,
            () -> new BookingPolicy(30, 240, 0, 60, 360, 1320, 5, true));
    }

    @Test
    void copiesChangeOneFieldAtATime() {
        BookingPolicy policy = BookingPolicy.defaults();
        assertFalse(policy.withWaitlistEnabled(false).waitlistEnabled());
        assertEquals(2, policy.withMaxActiveBookingsPerUser(2).maxActiveBookingsPerUser());
        assertEquals(8 * 60, policy.withOpeningWindow(8 * 60, 20 * 60).openingMinuteOfDayUtc());
        assertEquals(20 * 60, policy.withOpeningWindow(8 * 60, 20 * 60).closingMinuteOfDayUtc());
        assertEquals(policy.minDurationMinutes(), policy.withWaitlistEnabled(false).minDurationMinutes());
    }
}
