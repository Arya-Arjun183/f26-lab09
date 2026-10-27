package edu.cmu.cs214.campusreserve.core.service;

import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import edu.cmu.cs214.campusreserve.core.support.InMemoryStores;
import edu.cmu.cs214.campusreserve.core.support.RecordingPublisher;
import edu.cmu.cs214.campusreserve.core.support.TestClock;
import org.junit.jupiter.api.BeforeEach;

/** Wiring shared by the booking service suites. */
abstract class BookingServiceTestBase {

    protected InMemoryStores.Bookings bookings;
    protected InMemoryStores.Rooms rooms;
    protected InMemoryStores.Users users;
    protected InMemoryStores.Waitlist waitlist;
    protected RecordingPublisher publisher;
    protected TestClock clock;
    protected BookingService service;

    @BeforeEach
    void wireService() {
        bookings = new InMemoryStores.Bookings();
        rooms = new InMemoryStores.Rooms();
        users = new InMemoryStores.Users();
        waitlist = new InMemoryStores.Waitlist();
        publisher = new RecordingPublisher();
        clock = TestClock.at(Fixtures.NOW);
        service = build(BookingPolicy.defaults());
        rooms.save(Fixtures.studyRoom("r-1", 10));
        rooms.save(Fixtures.classroom("r-2"));
        users.save(Fixtures.user("u-1"));
        users.save(Fixtures.user("u-2"));
        users.save(Fixtures.user("u-3"));
    }

    /** Rebuilds the service on a different policy, keeping the same stores. */
    protected BookingService build(BookingPolicy policy) {
        service = new BookingService(
            bookings, rooms, users, waitlist, publisher, IdGenerator.sequential(), policy, clock);
        return service;
    }

    /** Books a room for a user, asserting nothing. */
    protected BookingOutcome book(String roomId, String userId, TimeInterval interval) {
        return service.requestBooking(Fixtures.request(roomId, userId, interval));
    }

    /** Books a room for a user with a stated party size. */
    protected BookingOutcome book(String roomId, String userId, TimeInterval interval, int attendees) {
        return service.requestBooking(Fixtures.request(roomId, userId, interval, attendees));
    }

    /** Books and returns the confirmed booking id, failing if it was not confirmed. */
    protected String bookAndGetId(String roomId, String userId, TimeInterval interval) {
        BookingOutcome outcome = book(roomId, userId, interval);
        return outcome.confirmedBooking().orElseThrow(
            () -> new AssertionError("Expected a confirmation but got " + outcome)).id();
    }

    /** Adds another room to the catalog. */
    protected void addRoom(Room room) {
        rooms.save(room);
    }

    /** Adds another user to the registry. */
    protected void addUser(User user) {
        users.save(user);
    }
}
