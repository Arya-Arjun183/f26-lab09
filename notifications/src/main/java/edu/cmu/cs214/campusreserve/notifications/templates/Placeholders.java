package edu.cmu.cs214.campusreserve.notifications.templates;

/**
 * The placeholder names the shipped templates use.
 *
 * <p>Callers that build value maps by hand use these constants so a rename of a
 * name shows up as a compile error in one place instead of as a rendering
 * failure at run time.
 */
public final class Placeholders {

    /** What to call the person in a greeting. */
    public static final String NAME = "name";

    /** The booking number. */
    public static final String BOOKING_ID = "bookingId";

    /** The room the booking holds or held. */
    public static final String ROOM_ID = "roomId";

    /** The booked interval, already written out for a person to read. */
    public static final String WHEN = "when";

    /** How many people the booking is for. */
    public static final String ATTENDEES = "attendees";

    /** The interval the booking held before it was changed, written out. */
    public static final String PREVIOUS_WHEN = "previousWhen";

    /** How many people the booking was for before it was changed. */
    public static final String PREVIOUS_ATTENDEES = "previousAttendees";

    /** Where the entry sits on the waiting list. */
    public static final String POSITION = "position";

    private Placeholders() {
    }
}
