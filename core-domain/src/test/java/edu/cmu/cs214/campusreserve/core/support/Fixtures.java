package edu.cmu.cs214.campusreserve.core.support;

import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;

/**
 * Small builders shared by the core-domain tests. The fixed date keeps every
 * assertion readable: the tests run on 2026-10-19 UTC unless they say otherwise.
 */
public final class Fixtures {

    /** The day the domain tests work on. */
    public static final String DAY = "2026-10-20";

    /** The instant the domain tests treat as now. */
    public static final String NOW = "2026-10-19T09:00:00Z";

    private Fixtures() {
    }

    /** An interval on the shared test day, given UTC hour and minute offsets. */
    public static TimeInterval interval(String startTime, String endTime) {
        return TimeInterval.parse(DAY + "T" + startTime + ":00Z", DAY + "T" + endTime + ":00Z");
    }

    /** An interval on a named day. */
    public static TimeInterval intervalOn(String day, String startTime, String endTime) {
        return TimeInterval.parse(day + "T" + startTime + ":00Z", day + "T" + endTime + ":00Z");
    }

    /** A ten-seat study room. */
    public static Room studyRoom(String id) {
        return Room.active(id, "Study " + id, "Sorrells", 10, RoomKind.STUDY_ROOM);
    }

    /** A study room of a given size. */
    public static Room studyRoom(String id, int capacity) {
        return new Room(id, "Study " + id, "Sorrells", capacity, RoomKind.STUDY_ROOM, true);
    }

    /** A forty-seat classroom. */
    public static Room classroom(String id) {
        return Room.active(id, "Classroom " + id, "Wean", 40, RoomKind.CLASSROOM);
    }

    /** A user with a course address. */
    public static User user(String id) {
        return new User(id, "User " + id, id + "@andrew.cmu.edu");
    }

    /** A request for four people. */
    public static BookingRequest request(String roomId, String userId, TimeInterval interval) {
        return BookingRequest.of(roomId, userId, interval, 4);
    }

    /** A request for a stated party size. */
    public static BookingRequest request(String roomId, String userId, TimeInterval interval, int attendees) {
        return BookingRequest.of(roomId, userId, interval, attendees);
    }
}
