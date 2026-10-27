package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;

/**
 * The message a person gets when a booking of theirs changes.
 *
 * <p>The message carries what the booking was as well as what it is now, so the
 * reader can tell at a glance whether the change is the one they asked for.
 *
 * <p>Wants {@link Placeholders#NAME}, {@link Placeholders#ROOM_ID},
 * {@link Placeholders#WHEN}, {@link Placeholders#ATTENDEES},
 * {@link Placeholders#PREVIOUS_WHEN}, {@link Placeholders#PREVIOUS_ATTENDEES},
 * and {@link Placeholders#BOOKING_ID}.
 */
public final class BookingModifiedTemplate extends TextMessageTemplate {

    private static final String SUBJECT = "Room {roomId} is now booked for {when}";

    private static final String BODY = """
        Hi {name},

        Your booking for room {roomId} has changed.

        It was {previousWhen} for {previousAttendees} people.
        It is now {when} for {attendees} people.

        Your booking number is still {bookingId}.

        If the new time does not suit you, change it again or cancel the booking.
        """;

    /** Builds the template. */
    public BookingModifiedTemplate() {
        super(NotificationKind.BOOKING_MODIFIED, SUBJECT, BODY);
    }
}
