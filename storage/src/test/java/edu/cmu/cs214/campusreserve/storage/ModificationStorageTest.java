package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.ModificationResult;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.events.BookingModifiedEvent;
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
 * Storing a booking that has been changed, and the line the change occupies in
 * the event log.
 */
class ModificationStorageTest {

    private static final TimeInterval SLOT = StorageFixtures.interval("10:00", "11:00");
    private static final TimeInterval MOVED = StorageFixtures.interval("13:00", "14:00");

    private StoreRegistry registry;
    private BookingService service;

    @BeforeEach
    void setUp() {
        registry = StoreRegistry.inMemory();
        registry.rooms().save(StorageFixtures.room("r-1"));
        registry.users().save(StorageFixtures.user("u-1"));
        registry.users().save(StorageFixtures.user("u-2"));
        service = new BookingService(
            registry.bookings(),
            registry.rooms(),
            registry.users(),
            registry.waitlist(),
            registry.publisher(),
            IdGenerator.sequential(),
            BookingPolicy.defaults(),
            Clock.fixed(StorageFixtures.NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void writesTheModifiedEventFields() {
        assertEquals(
            "{\"event\":\"booking-modified\",\"occurredAt\":\"2026-10-19T09:00:00Z\","
                + "\"bookingId\":\"bk-1\",\"roomId\":\"r-1\",\"userId\":\"u-1\","
                + "\"start\":\"2026-10-20T13:00:00Z\",\"end\":\"2026-10-20T14:00:00Z\","
                + "\"attendees\":6,\"previousStart\":\"2026-10-20T10:00:00Z\","
                + "\"previousEnd\":\"2026-10-20T11:00:00Z\",\"previousAttendees\":4}",
            EventLogFormat.toLine(modifiedEvent()));
    }

    @Test
    void declaresTheModifiedWireName() {
        assertTrue(List.of(EventLogFormat.eventTypes()).contains("booking-modified"));
        assertTrue(EventLogFormat.isKnownType("booking-modified"));
    }

    @Test
    void theStoreKeepsWhatTheBookingUsedToBe() {
        String id = book("u-1", SLOT);
        service.modifyBooking(id, "u-1", MOVED, 6);

        Booking stored = registry.bookings().findById(id).orElseThrow();
        assertEquals(MOVED, stored.interval());
        assertEquals(6, stored.attendees());
        assertEquals(1, stored.modifications().size());
        assertEquals(SLOT, stored.modifications().get(0).previousInterval());
        assertEquals(1, registry.bookings().count());
    }

    @Test
    void aModificationLandsInTheLogAfterTheBookingItChanged() {
        String id = book("u-1", SLOT);
        ModificationResult result = service.modifyBooking(id, "u-1", MOVED, 4);

        assertTrue(result.isModified());
        assertEquals(List.of("booking-created", "booking-modified"),
            registry.memoryEventLog().events().stream().map(event -> event.type()).toList());
    }

    @Test
    void freedTimePromotesAWaiterAndBothStepsAreLogged() {
        String id = book("u-1", SLOT);
        service.requestBooking(BookingRequest.of("r-1", "u-2", SLOT, 4));

        service.modifyBooking(id, "u-1", MOVED, 4);

        assertEquals(List.of("booking-created", "booking-modified", "waitlist-promoted"),
            registry.memoryEventLog().events().stream().map(event -> event.type()).toList());
        assertEquals(0, registry.waitlist().countForRoom("r-1"));
    }

    @Test
    void theExportedLogCarriesTheModifiedLine(@TempDir Path directory) throws IOException {
        String id = book("u-1", SLOT);
        service.modifyBooking(id, "u-1", MOVED, 6);

        Path file = directory.resolve("events.jsonl");
        assertEquals(2, registry.memoryEventLog().writeTo(file));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.get(1).contains("\"event\":\"booking-modified\""));
        assertTrue(lines.get(1).contains("\"previousStart\":\"2026-10-20T10:00:00Z\""));
        assertTrue(lines.get(1).contains("\"attendees\":6"));
    }

    private String book(String userId, TimeInterval interval) {
        return service.requestBooking(BookingRequest.of("r-1", userId, interval, 4))
            .confirmedBooking().orElseThrow().id();
    }

    private static BookingModifiedEvent modifiedEvent() {
        Booking moved = StorageFixtures.booking("bk-1", "r-1", "u-1", "10:00", "11:00")
            .modifiedTo(MOVED, 6, StorageFixtures.NOW);
        return BookingModifiedEvent.of(moved, moved.lastModification().orElseThrow(), StorageFixtures.NOW);
    }
}
