package edu.cmu.cs214.campusreserve.cli;

import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * A campus worth of rooms, users, and bookings, loaded into a fresh platform so
 * the other commands have something to read.
 *
 * <p>The data is fixed except for the day it sits on, which is the day after
 * whatever the context clock says. Loading the same data against the same clock
 * twice gives the same ids, the same bookings, and the same waitlist.
 *
 * <p>The last booking below asks for time the first one already holds. That is
 * deliberate: it leaves a waitlist entry behind, so {@code waitlist} and
 * {@code cancel} have something to show.
 */
public final class DemoData {

    /** The rooms this data ships, in the order they are registered. */
    private static final List<Room> ROOMS = List.of(
        Room.active("wean-5207", "Wean 5207", "Wean Hall", 8, RoomKind.STUDY_ROOM),
        Room.active("wean-5409", "Wean 5409", "Wean Hall", 12, RoomKind.STUDY_ROOM),
        Room.active("hunt-a15", "Hunt A15", "Hunt Library", 6, RoomKind.STUDY_ROOM),
        Room.active("gates-4102", "Gates 4102", "Gates Center", 40, RoomKind.CLASSROOM),
        Room.active("porter-100", "Porter 100", "Porter Hall", 60, RoomKind.CLASSROOM),
        Room.active("gates-4215", "Gates 4215", "Gates Center", 24, RoomKind.LAB),
        Room.active("gates-3001", "Gates 3001", "Gates Center", 16, RoomKind.LAB),
        Room.active("baker-a51", "Baker A51", "Baker Hall", 250, RoomKind.AUDITORIUM));

    /** The users this data ships, in the order they are registered. */
    private static final List<User> USERS = List.of(
        new User("u-avery", "Avery Chen", "avery@campus.example.edu"),
        new User("u-blair", "Blair Okafor", "blair@campus.example.edu"),
        new User("u-casey", "Casey Nowak", "casey@campus.example.edu"),
        new User("u-devi", "Devi Raman", "devi@campus.example.edu"),
        new User("u-elliot", "Elliot Marsh", "elliot@campus.example.edu"));

    /** One booking to place, given the day the data lands on. */
    private record Slot(String roomId, String userId, LocalTime start, int minutes, int attendees) {
    }

    /** The bookings this data places, in order. */
    private static final List<Slot> SLOTS = List.of(
        new Slot("wean-5207", "u-avery", LocalTime.of(9, 0), 60, 4),
        new Slot("wean-5207", "u-blair", LocalTime.of(11, 0), 90, 6),
        new Slot("gates-4215", "u-elliot", LocalTime.of(10, 0), 120, 20),
        new Slot("gates-4102", "u-casey", LocalTime.of(13, 0), 120, 30),
        new Slot("baker-a51", "u-devi", LocalTime.of(15, 0), 180, 120),
        new Slot("hunt-a15", "u-avery", LocalTime.of(14, 0), 45, 3),
        new Slot("wean-5207", "u-casey", LocalTime.of(9, 30), 60, 2));

    private DemoData() {
    }

    /** What a load put into the platform. */
    public record Summary(int rooms, int users, int confirmed, int waitlisted, int refused) {
    }

    /**
     * Registers the rooms and users, then places the bookings through the booking
     * service so the event log fills the way a real run would fill it.
     *
     * @param context the platform to load into
     * @return counts of what went in
     */
    public static Summary load(CliContext context) {
        Objects.requireNonNull(context, "context");
        ROOMS.forEach(context.rooms()::register);
        USERS.forEach(context.users()::register);

        LocalDate day = context.today().plusDays(1);
        List<BookingOutcome> outcomes = SLOTS.stream()
            .map(slot -> context.bookings().requestBooking(BookingRequest.of(
                slot.roomId(),
                slot.userId(),
                TimeInterval.ofMinutes(day.atTime(slot.start()).toInstant(ZoneOffset.UTC), slot.minutes()),
                slot.attendees())))
            .toList();
        return new Summary(ROOMS.size(), USERS.size(),
            (int) outcomes.stream().filter(BookingOutcome::isConfirmed).count(),
            (int) outcomes.stream().filter(BookingOutcome::isWaitlisted).count(),
            (int) outcomes.stream().filter(BookingOutcome::isRejected).count());
    }
}
