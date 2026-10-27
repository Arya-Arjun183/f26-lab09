package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;

/**
 * The message a person gets when a booking of theirs takes a room.
 *
 * <p>Wants {@link Placeholders#NAME}, {@link Placeholders#ROOM_ID},
 * {@link Placeholders#WHEN}, {@link Placeholders#ATTENDEES}, and
 * {@link Placeholders#BOOKING_ID}.
 */
public final class BookingConfirmedTemplate extends TextMessageTemplate {

    private static final String SUBJECT = "Room {roomId} is booked for {when}";

    private static final String BODY = """
        Hi {name},

        Room {roomId} is yours for {when}. We have it down for {attendees} people.
        Your booking number is {bookingId}.

        If you no longer need the room, cancel the booking so someone else can take it.
        """;

    /** Builds the template. */
    public BookingConfirmedTemplate() {
        super(NotificationKind.BOOKING_CONFIRMED, SUBJECT, BODY);
    }
}
