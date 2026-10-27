package edu.cmu.cs214.campusreserve.core.domain;

import java.util.Objects;

/** What a caller asks for when it wants a room. */
public record BookingRequest(String roomId, String userId, TimeInterval interval, int attendees) {

    public BookingRequest {
        roomId = Identifiers.require(roomId, "room id");
        userId = Identifiers.require(userId, "user id");
        Objects.requireNonNull(interval, "interval");
        if (attendees <= 0) {
            throw new IllegalArgumentException("Attendees must be positive: " + attendees);
        }
    }

    /** Builds a request. */
    public static BookingRequest of(String roomId, String userId, TimeInterval interval, int attendees) {
        return new BookingRequest(roomId, userId, interval, attendees);
    }
}
