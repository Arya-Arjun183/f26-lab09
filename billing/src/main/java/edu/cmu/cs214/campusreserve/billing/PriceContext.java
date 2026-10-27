package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.Objects;

/**
 * Everything a price rule is allowed to look at: which room, for how long, for
 * how many people, and at which prices.
 *
 * <p>A rule gets a context and nothing else. It cannot reach a store, a clock,
 * or another rule, so the same context always produces the same line items.
 *
 * @param room the room being priced
 * @param interval the booked time
 * @param attendees how many people the booking is for, at least one
 * @param rateCard the prices in force for this quote
 */
public record PriceContext(Room room, TimeInterval interval, int attendees, RateCard rateCard) {

    /** Minutes in the hour the hourly rate is quoted against. */
    private static final long MINUTES_PER_HOUR = 60L;

    public PriceContext {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(rateCard, "rateCard");
        if (attendees <= 0) {
            throw new IllegalArgumentException("Attendees must be positive: " + attendees);
        }
    }

    /** Builds a context from the parts. */
    public static PriceContext of(Room room, TimeInterval interval, int attendees, RateCard rateCard) {
        return new PriceContext(room, interval, attendees, rateCard);
    }

    /**
     * Builds the context for pricing a booking. The room must be the one the
     * booking names, since the rate follows the room's kind.
     */
    public static PriceContext forBooking(Booking booking, Room room, RateCard rateCard) {
        Objects.requireNonNull(booking, "booking");
        Objects.requireNonNull(room, "room");
        if (!booking.roomId().equals(room.id())) {
            throw new IllegalArgumentException(
                "Booking " + booking.id() + " is for room " + booking.roomId() + ", not " + room.id());
        }
        return new PriceContext(room, booking.interval(), booking.attendees(), rateCard);
    }

    /** What kind of room this is, which is what the hourly rate keys on. */
    public RoomKind kind() {
        return room.kind();
    }

    /** The hourly rate for this room's kind. */
    public Money hourlyRate() {
        return rateCard.hourlyRate(kind());
    }

    /** Nothing at all in this quote's currency, which is the rate card's. */
    public Money zero() {
        return rateCard.zero();
    }

    /** How many whole minutes the booking runs for. */
    public long minutes() {
        return interval.durationMinutes();
    }

    /** Minutes since UTC midnight at the moment the booking starts. */
    public int startMinuteOfDayUtc() {
        return interval.startMinuteOfDayUtc();
    }

    /**
     * The booked time at the room kind's hourly rate, billed by the minute and
     * rounded half up to the cent. A 90 minute study room booking at USD 5.00 an
     * hour comes to USD 7.50.
     *
     * <p>The base charge rule turns this into a line item, and the percentage
     * rules scale off it rather than off the running total. That is deliberate:
     * a surcharge is a percentage of room time, never a percentage of another
     * surcharge, so adding a rule to the middle of the pipeline cannot silently
     * inflate the ones after it.
     */
    public Money baseCharge() {
        return hourlyRate().prorate(minutes(), MINUTES_PER_HOUR);
    }
}
