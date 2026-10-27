package edu.cmu.cs214.campusreserve.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One change a booker made to a booking they hold: what it was, what it became,
 * and when.
 *
 * <p>A modification always changes something. A request that asks for the times
 * and the party size a booking already has is not refused, but it produces no
 * record of this kind, so the history never carries an entry saying a booking
 * stayed the same.
 *
 * @param at when the change was made
 * @param previousInterval the interval the booking held before the change
 * @param previousAttendees the party size before the change
 * @param interval the interval the booking holds after the change
 * @param attendees the party size after the change
 */
public record BookingModification(
    Instant at,
    TimeInterval previousInterval,
    int previousAttendees,
    TimeInterval interval,
    int attendees) {

    public BookingModification {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(previousInterval, "previousInterval");
        Objects.requireNonNull(interval, "interval");
        if (previousAttendees <= 0) {
            throw new IllegalArgumentException(
                "Previous attendees must be positive: " + previousAttendees);
        }
        if (attendees <= 0) {
            throw new IllegalArgumentException("Attendees must be positive: " + attendees);
        }
        if (previousInterval.equals(interval) && previousAttendees == attendees) {
            throw new IllegalArgumentException("A modification has to change something");
        }
    }

    /** Did the booking move, lengthen, or shorten? */
    public boolean movedInTime() {
        return !previousInterval.equals(interval);
    }

    /** Did the party size change? */
    public boolean resized() {
        return previousAttendees != attendees;
    }
}
