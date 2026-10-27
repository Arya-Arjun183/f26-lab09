package edu.cmu.cs214.campusreserve.cli;

import edu.cmu.cs214.campusreserve.billing.InvoiceService;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.core.service.AvailabilityService;
import edu.cmu.cs214.campusreserve.core.service.BookingService;
import edu.cmu.cs214.campusreserve.core.service.RoomCatalog;
import edu.cmu.cs214.campusreserve.core.service.UserRegistry;
import edu.cmu.cs214.campusreserve.storage.InMemoryEventLog;
import edu.cmu.cs214.campusreserve.storage.StoreRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * One running platform for the length of one command: the stores, the services
 * built over them, and the clock they all read.
 *
 * <p>The wiring lives in the factories here and nowhere else, so a command never
 * builds a service of its own and every command in a run sees the same stores.
 * Ids count up from one, which keeps a seeded platform reproducible.
 *
 * <p>The context owns the store registry and closes it, so callers use it in a
 * try-with-resources block.
 *
 * @param stores the stores and the publisher feeding the event log
 * @param bookings the booking flow: request, cancel, waitlist
 * @param rooms the room list
 * @param users the users who can hold a booking
 * @param availability free and busy time in a room
 * @param invoices billing over the bookings in this platform
 * @param policy the limits the booking service is enforcing
 * @param clock what every service in this context reads the time from
 */
public record CliContext(
    StoreRegistry stores,
    BookingService bookings,
    RoomCatalog rooms,
    UserRegistry users,
    AvailabilityService availability,
    InvoiceService invoices,
    BookingPolicy policy,
    Clock clock) implements AutoCloseable {

    /** A fresh platform on the system clock, which is what the entry point uses. */
    public static CliContext inMemory() {
        return inMemory(Clock.systemUTC());
    }

    /** A fresh platform on a clock the caller controls. */
    public static CliContext inMemory(Clock clock) {
        BookingPolicy policy = BookingPolicy.defaults();
        StoreRegistry stores = StoreRegistry.inMemory();
        // Bookings and invoices count separately, so bk-1 has inv-1 next to it.
        IdGenerator invoiceIds = IdGenerator.sequential();
        return new CliContext(
            stores,
            new BookingService(stores.bookings(), stores.rooms(), stores.users(), stores.waitlist(),
                stores.publisher(), IdGenerator.sequential(), policy, clock),
            new RoomCatalog(stores.rooms()),
            new UserRegistry(stores.users()),
            new AvailabilityService(stores.bookings(), policy),
            InvoiceService.standard(stores.bookings(), stores.rooms(), invoiceIds, clock),
            policy,
            clock);
    }

    /** The event log every published event has landed in. */
    public InMemoryEventLog eventLog() {
        return stores.memoryEventLog();
    }

    /** Today, on the UTC calendar the whole platform works in. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    @Override
    public void close() {
        stores.close();
    }
}
