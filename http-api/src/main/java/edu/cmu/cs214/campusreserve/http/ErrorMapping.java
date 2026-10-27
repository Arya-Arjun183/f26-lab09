package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;

/**
 * Turns a domain rejection into an HTTP status.
 *
 * <p>The rule is about who can fix the problem. A record that is not there is a
 * 404. A caller who may not act is a 403. A request that collides with the
 * current state of the calendar is a 409, because the same request might succeed
 * later. Everything else is the caller sending something the rules do not allow,
 * which is a 400.
 */
public final class ErrorMapping {

    private ErrorMapping() {
    }

    /** The status to send for a rejection. */
    public static int statusFor(RejectionReason reason) {
        return switch (reason) {
            case ROOM_NOT_FOUND, USER_NOT_FOUND, BOOKING_NOT_FOUND, WAITLIST_ENTRY_NOT_FOUND ->
                Status.NOT_FOUND;
            case NOT_BOOKING_OWNER -> Status.FORBIDDEN;
            case ROOM_UNAVAILABLE,
                 USER_DOUBLE_BOOKED,
                 ALREADY_ON_WAITLIST,
                 BOOKING_ALREADY_CANCELLED,
                 BOOKING_ALREADY_STARTED,
                 USER_BOOKING_LIMIT_REACHED,
                 ROOM_INACTIVE -> Status.CONFLICT;
            default -> Status.BAD_REQUEST;
        };
    }

    /** Is this rejection one the caller could fix by trying a different time? */
    public static boolean isRetryable(RejectionReason reason) {
        return statusFor(reason) == Status.CONFLICT;
    }
}
