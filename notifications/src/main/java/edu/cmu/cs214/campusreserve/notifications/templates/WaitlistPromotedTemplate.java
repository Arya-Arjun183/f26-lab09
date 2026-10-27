package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;

/**
 * The message a person gets when their waiting-list entry turns into a booking.
 *
 * <p>Wants {@link Placeholders#NAME}, {@link Placeholders#ROOM_ID},
 * {@link Placeholders#WHEN}, {@link Placeholders#ATTENDEES},
 * {@link Placeholders#POSITION}, and {@link Placeholders#BOOKING_ID}.
 */
public final class WaitlistPromotedTemplate extends TextMessageTemplate {

    private static final String SUBJECT = "Room {roomId} is yours for {when}";

    private static final String BODY = """
        Hi {name},

        A place opened up. Room {roomId} is now booked for you for {when}, for {attendees} people.
        You were number {position} on the waiting list.
        Your booking number is {bookingId}.

        Nothing more is needed from you. Cancel the booking if you cannot use it.
        """;

    /** Builds the template. */
    public WaitlistPromotedTemplate() {
        super(NotificationKind.WAITLIST_PROMOTED, SUBJECT, BODY);
    }
}
