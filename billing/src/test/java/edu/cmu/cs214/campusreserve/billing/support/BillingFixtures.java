package edu.cmu.cs214.campusreserve.billing.support;

import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.RateCard;
import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;

/**
 * Builders shared by the billing tests. Every test works on one UTC day so the
 * peak window arithmetic stays readable: the standard window runs 13:00 to 17:00
 * UTC, so 09:00 is off peak and 14:00 is on it.
 */
public final class BillingFixtures {

    /** The day the billing tests price bookings on. */
    public static final String DAY = "2026-11-09";

    /** The instant the billing tests treat as now. */
    public static final String NOW = "2026-11-09T08:00:00Z";

    private BillingFixtures() {
    }

    /** An interval on the shared day, given UTC clock times such as {@code 09:00}. */
    public static TimeInterval interval(String startTime, String endTime) {
        return TimeInterval.parse(DAY + "T" + startTime + ":00Z", DAY + "T" + endTime + ":00Z");
    }

    /** A ten-seat study room. */
    public static Room studyRoom(String id) {
        return Room.active(id, "Study " + id, "Sorrells", 10, RoomKind.STUDY_ROOM);
    }

    /** A two hundred seat auditorium. */
    public static Room auditorium(String id) {
        return Room.active(id, "Hall " + id, "Wean", 200, RoomKind.AUDITORIUM);
    }

    /** A confirmed booking created at {@link #NOW}. */
    public static Booking booking(String id, Room room, String userId, TimeInterval interval, int attendees) {
        return Booking.confirmed(id, room.id(), userId, interval, attendees, Instant.parse(NOW));
    }

    /** A context on the standard rate card. */
    public static PriceContext context(Room room, TimeInterval interval, int attendees) {
        return PriceContext.of(room, interval, attendees, RateCard.standard());
    }

    /** A context on a rate card the test has adjusted. */
    public static PriceContext context(Room room, TimeInterval interval, int attendees, RateCard card) {
        return PriceContext.of(room, interval, attendees, card);
    }
}
