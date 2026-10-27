package edu.cmu.cs214.campusreserve.core.domain;

/** Where a booking stands. A cancelled booking stays in the store as history. */
public enum BookingStatus {

    /** The booking holds the room. It takes part in conflict checks. */
    CONFIRMED,

    /** The booking was cancelled. It no longer holds the room. */
    CANCELLED;

    /** Does a booking in this status hold the room against other bookings? */
    public boolean holdsRoom() {
        return this == CONFIRMED;
    }
}
