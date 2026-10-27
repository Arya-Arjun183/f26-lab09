package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.core.service.BookingService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The booking flow running on the stores this module ships, rather than on the
 * test doubles the core-domain suite uses. This is where the ports and their
 * implementations are checked against each other.
 */
class BookingFlowStorageTest {

    private static final TimeInterval SLOT = StorageFixtures.interval("10:00", "11:00");

    private StoreRegistry registry;
    private BookingService service;

    @BeforeEach
    void setUp() {
        registry = StoreRegistry.inMemory();
        seed(registry);
        service = serviceFor(registry);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    private static void seed(StoreRegistry target) {
        target.rooms().save(StorageFixtures.room("r-1"));
        target.rooms().save(StorageFixtures.room("r-2"));
        target.users().save(StorageFixtures.user("u-1"));
        target.users().save(StorageFixtures.user("u-2"));
        target.users().save(StorageFixtures.user("u-3"));
    }

    private static BookingService serviceFor(StoreRegistry target) {
        return new BookingService(
            target.bookings(),
            target.rooms(),
            target.users(),
            target.waitlist(),
            target.publisher(),
            IdGenerator.sequential(),
            BookingPolicy.defaults(),
            Clock.fixed(StorageFixtures.NOW, ZoneOffset.UTC));
    }

    private BookingOutcome book(String roomId, String userId, TimeInterval interval) {
        return service.requestBooking(BookingRequest.of(roomId, userId, interval, 4));
    }

    @Test
    void aBookingLandsInTheStoreAndInTheLog() {
        BookingOutcome outcome = book("r-1", "u-1", SLOT);
        assertTrue(outcome.isConfirmed());
        assertEquals(1, registry.bookings().count());
        assertEquals(1, registry.memoryEventLog().appended());
        assertEquals("booking-created", registry.memoryEventLog().events().get(0).type());
    }

    @Test
    void aTakenSlotLandsOnTheWaitlistWithoutAnEvent() {
        book("r-1", "u-1", SLOT);
        assertTrue(book("r-1", "u-2", SLOT).isWaitlisted());
        assertEquals(1, registry.waitlist().count());
        assertEquals(1, registry.memoryEventLog().appended());
    }

    @Test
    void cancellationPromotesTheLongestWaiterAndLogsBothSteps() {
        String held = book("r-1", "u-1", SLOT).confirmedBooking().orElseThrow().id();
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", SLOT);

        CancellationResult result = service.cancelBooking(held, "u-1");

        assertEquals(1, result.promotions().size());
        assertEquals("u-2", result.promotions().get(0).booking().userId());
        assertEquals(List.of("booking-created", "booking-cancelled", "waitlist-promoted"),
            registry.memoryEventLog().events().stream().map(e -> e.type()).toList());
        assertEquals(1, registry.waitlist().countForRoom("r-1"));
    }

    @Test
    void theStoreNeverHoldsTwoBookingsOverTheSameRoomTime() {
        book("r-1", "u-1", SLOT);
        book("r-1", "u-2", SLOT);
        book("r-1", "u-3", StorageFixtures.interval("10:30", "11:30"));
        assertEquals(1, registry.bookings().findActiveByRoom("r-1").size());
    }

    @Test
    void theLogWrittenByAFullFlowIsReadableJsonLines(@TempDir Path directory) throws IOException {
        String held = book("r-1", "u-1", SLOT).confirmedBooking().orElseThrow().id();
        book("r-1", "u-2", SLOT);
        service.cancelBooking(held, "u-1");

        Path file = directory.resolve("events.jsonl");
        assertEquals(3, registry.memoryEventLog().writeTo(file));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        for (String line : lines) {
            assertTrue(line.startsWith("{\"event\":\""));
            assertTrue(line.endsWith("}"));
            assertTrue(line.contains("\"occurredAt\":\"2026-10-19T09:00:00Z\""));
        }
        assertTrue(lines.get(2).contains("\"event\":\"waitlist-promoted\""));
    }

    @Test
    void aFileBackedRegistryWritesTheSameLines(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("events.jsonl");
        try (StoreRegistry fileBacked = StoreRegistry.withEventLogFile(file)) {
            seed(fileBacked);
            BookingService fileService = serviceFor(fileBacked);
            fileService.requestBooking(BookingRequest.of("r-1", "u-1", SLOT, 4));
            fileBacked.eventLog().flush();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"booking-created\""));
        assertTrue(lines.get(0).contains("\"roomId\":\"r-1\""));
    }
}
