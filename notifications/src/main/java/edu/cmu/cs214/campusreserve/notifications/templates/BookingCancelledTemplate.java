package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;

/**
 * The message a person gets when a booking of theirs gives its room back.
 *
 * <p>Wants {@link Placeholders#NAME}, {@link Placeholders#ROOM_ID},
 * {@link Placeholders#WHEN}, and {@link Placeholders#BOOKING_ID}.
 */
public final class BookingCancelledTemplate extends TextMessageTemplate {

    private static final String SUBJECT = "Booking for room {roomId} is cancelled";

    private static final String BODY = """
        Hi {name},

        Your booking of room {roomId} for {when} is cancelled.
        The booking number was {bookingId}.

        The room is free again, and anyone on the waiting list for it is next in line.
        """;

    /** Builds the template. */
    public BookingCancelledTemplate() {
        super(NotificationKind.BOOKING_CANCELLED, SUBJECT, BODY);
    }
}
