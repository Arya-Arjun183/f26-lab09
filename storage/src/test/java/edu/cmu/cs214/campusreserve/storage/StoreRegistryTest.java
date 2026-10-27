package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import edu.cmu.cs214.campusreserve.core.ports.EventPublisher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreRegistryTest {

    @Test
    void buildsAFullSetOfEmptyStores() {
        try (StoreRegistry registry = StoreRegistry.inMemory()) {
            assertEquals(0, registry.rooms().count());
            assertEquals(0, registry.users().count());
            assertEquals(0, registry.bookings().count());
            assertEquals(0, registry.waitlist().count());
            assertEquals(0, registry.publisher().subscriberCount());
        }
    }

    @Test
    void publishingLandsInTheEventLog() {
        try (StoreRegistry registry = StoreRegistry.inMemory()) {
            registry.publisher().publish(StorageFixtures.created("bk-1"));
            registry.publisher().publish(StorageFixtures.cancelled("bk-1"));
            assertEquals(2, registry.memoryEventLog().appended());
            assertEquals(List.of("booking-created", "booking-cancelled"),
                registry.memoryEventLog().events().stream().map(DomainEvent::type).toList());
        }
    }

    @Test
    void subscribersSeeEveryEvent() {
        List<String> seen = new ArrayList<>();
        try (StoreRegistry registry = StoreRegistry.inMemory()) {
            registry.subscribe(event -> seen.add(event.type()));
            registry.publisher().publish(StorageFixtures.created("bk-1"));
            assertEquals(List.of("booking-created"), seen);
            assertEquals(1, registry.publisher().subscriberCount());
        }
    }

    @Test
    void aFailingSubscriberDoesNotStopTheOthersOrTheLog() {
        List<String> seen = new ArrayList<>();
        try (StoreRegistry registry = StoreRegistry.inMemory()) {
            EventPublisher broken = event -> {
                throw new IllegalStateException("transport is down");
            };
            registry.subscribe(broken).subscribe(event -> seen.add(event.bookingId()));

            registry.publisher().publish(StorageFixtures.created("bk-1"));

            assertEquals(List.of("bk-1"), seen);
            assertEquals(1, registry.memoryEventLog().appended());
            assertEquals(1, registry.publisher().failures().size());
            assertEquals("booking-created", registry.publisher().failures().get(0).eventType());
            assertTrue(registry.publisher().failures().get(0).message().contains("transport is down"));
        }
    }

    @Test
    void writesStraightToAFileWhenAskedTo(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("events.jsonl");
        try (StoreRegistry registry = StoreRegistry.withEventLogFile(file)) {
            registry.publisher().publish(StorageFixtures.created("bk-1"));
            registry.eventLog().flush();
            assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
        }
    }

    @Test
    void aFileBackedRegistryHasNoInMemoryLog(@TempDir Path directory) {
        Path file = directory.resolve("events.jsonl");
        try (StoreRegistry registry = StoreRegistry.withEventLogFile(file)) {
            assertThrows(IllegalStateException.class, registry::memoryEventLog);
        }
    }

    @Test
    void acceptsAnEventLogTheCallerBuilt() {
        InMemoryEventLog log = new InMemoryEventLog();
        try (StoreRegistry registry = StoreRegistry.with(log)) {
            registry.publisher().publish(StorageFixtures.created("bk-1"));
        }
        assertEquals(1, log.appended());
    }

    @Test
    void clearingStoresLeavesTheEventLogAlone() {
        try (StoreRegistry registry = StoreRegistry.inMemory()) {
            registry.rooms().save(StorageFixtures.room("r-1"));
            registry.users().save(StorageFixtures.user("u-1"));
            registry.bookings().save(StorageFixtures.booking("bk-1", "r-1", "u-1", "10:00", "11:00"));
            registry.waitlist().save(StorageFixtures.entry("wl-1", "r-1", "u-2", 1));
            registry.publisher().publish(StorageFixtures.created("bk-1"));

            registry.clearStores();

            assertEquals(0, registry.rooms().count());
            assertEquals(0, registry.bookings().count());
            assertEquals(0, registry.waitlist().count());
            assertFalse(registry.users().exists("u-1"));
            assertEquals(1, registry.memoryEventLog().appended());
        }
    }

    @Test
    void thePublisherExposesTheLogItWritesTo() {
        InMemoryEventLog log = new InMemoryEventLog();
        EventLogPublisher publisher = new EventLogPublisher(log);
        assertEquals(log, publisher.log());
        assertTrue(publisher.failures().isEmpty());
    }
}
