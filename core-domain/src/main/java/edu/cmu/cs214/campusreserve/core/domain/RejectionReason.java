package edu.cmu.cs214.campusreserve.core.domain;

/**
 * Why the platform said no. Every rejection the service can produce is on this
 * list; callers map the code onto whatever surface they present.
 */
public enum RejectionReason {

    ROOM_NOT_FOUND("room-not-found", "No room with that id."),
    ROOM_INACTIVE("room-inactive", "That room is not taking bookings."),
    USER_NOT_FOUND("user-not-found", "No user with that id."),
    ATTENDEES_EXCEED_CAPACITY("attendees-exceed-capacity", "The room cannot seat that many people."),
    INTERVAL_IN_PAST("interval-in-past", "Bookings must start in the future."),
    INTERVAL_TOO_SHORT("interval-too-short", "The booking is shorter than the minimum."),
    INTERVAL_TOO_LONG("interval-too-long", "The booking is longer than this room allows."),
    INTERVAL_NOT_ALIGNED("interval-not-aligned", "Bookings must start and end on a slot boundary."),
    INTERVAL_SPANS_DAYS("interval-spans-days", "A booking must stay inside one UTC day."),
    OUTSIDE_OPENING_HOURS("outside-opening-hours", "The building is closed at that time."),
    TOO_FAR_IN_ADVANCE("too-far-in-advance", "That start date is further ahead than this room allows."),
    USER_BOOKING_LIMIT_REACHED("user-booking-limit-reached", "You already hold the maximum number of bookings."),
    USER_DOUBLE_BOOKED("user-double-booked", "You already hold a booking over that time."),
    ROOM_UNAVAILABLE("room-unavailable", "The room is taken over that time."),
    ALREADY_ON_WAITLIST("already-on-waitlist", "You are already on the waitlist for that slot."),
    BOOKING_NOT_FOUND("booking-not-found", "No booking with that id."),
    NOT_BOOKING_OWNER("not-booking-owner", "That booking belongs to someone else."),
    BOOKING_ALREADY_CANCELLED("booking-already-cancelled", "That booking is already cancelled."),
    BOOKING_ALREADY_STARTED("booking-already-started", "That booking has already started."),
    WAITLIST_ENTRY_NOT_FOUND("waitlist-entry-not-found", "No waitlist entry with that id.");

    private final String code;
    private final String message;

    RejectionReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** Stable machine-readable code. Callers may put this on the wire. */
    public String code() {
        return code;
    }

    /** Sentence a person can read. */
    public String message() {
        return message;
    }

    /** Is this a rejection about who is asking rather than about the slot? */
    public boolean isOwnershipProblem() {
        return this == NOT_BOOKING_OWNER;
    }

    /** Is this a rejection about a thing that does not exist? */
    public boolean isMissingRecord() {
        return this == ROOM_NOT_FOUND
            || this == USER_NOT_FOUND
            || this == BOOKING_NOT_FOUND
            || this == WAITLIST_ENTRY_NOT_FOUND;
    }
}
