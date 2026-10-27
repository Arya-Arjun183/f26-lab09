package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.ports.EventPublisher;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One place to build a full set of stores and the publisher that feeds the event
 * log. Every entry point in the platform (the HTTP server, the CLI, the tests)
 * wires itself from here so they all agree on what a running platform looks
 * like.
 */
public final class StoreRegistry implements AutoCloseable {

    private final InMemoryRoomStore rooms;
    private final InMemoryUserStore users;
    private final InMemoryBookingStore bookings;
    private final InMemoryWaitlistStore waitlist;
    private final EventLogWriter eventLog;
    private final EventLogPublisher publisher;

    private StoreRegistry(EventLogWriter eventLog) {
        this.rooms = new InMemoryRoomStore();
        this.users = new InMemoryUserStore();
        this.bookings = new InMemoryBookingStore();
        this.waitlist = new InMemoryWaitlistStore();
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        this.publisher = new EventLogPublisher(eventLog);
    }

    /** Stores backed by memory, with the event log kept in memory too. */
    public static StoreRegistry inMemory() {
        return new StoreRegistry(new InMemoryEventLog());
    }

    /** Stores backed by memory, with the event log appended to a JSONL file. */
    public static StoreRegistry withEventLogFile(Path path) {
        return new StoreRegistry(new JsonlFileEventLogWriter(path));
    }

    /** Stores backed by memory, with an event log the caller supplies. */
    public static StoreRegistry with(EventLogWriter eventLog) {
        return new StoreRegistry(eventLog);
    }

    /** The room store. */
    public InMemoryRoomStore rooms() {
        return rooms;
    }

    /** The user store. */
    public InMemoryUserStore users() {
        return users;
    }

    /** The booking store. */
    public InMemoryBookingStore bookings() {
        return bookings;
    }

    /** The waitlist store. */
    public InMemoryWaitlistStore waitlist() {
        return waitlist;
    }

    /** The event log behind the publisher. */
    public EventLogWriter eventLog() {
        return eventLog;
    }

    /**
     * The in-memory event log, when that is what this registry was built with.
     * Callers that want to export the log ask for this.
     */
    public InMemoryEventLog memoryEventLog() {
        if (eventLog instanceof InMemoryEventLog inMemory) {
            return inMemory;
        }
        throw new IllegalStateException("This registry writes its event log to a file, not to memory");
    }

    /** The publisher the booking service should be given. */
    public EventLogPublisher publisher() {
        return publisher;
    }

    /** Attaches a subscriber to the publisher and returns this registry. */
    public StoreRegistry subscribe(EventPublisher subscriber) {
        publisher.subscribe(subscriber);
        return this;
    }

    /** Empties every store. The event log is left alone. */
    public void clearStores() {
        rooms.clear();
        users.clear();
        bookings.clear();
        waitlist.clear();
    }

    @Override
    public void close() {
        eventLog.close();
    }
}
