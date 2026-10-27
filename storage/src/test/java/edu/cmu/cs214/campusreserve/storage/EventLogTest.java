package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogTest {

    @Nested
    @DisplayName("json line")
    class Lines {

        @Test
        void writesFieldsInTheOrderTheyWereAdded() {
            String line = JsonLine.object()
                .field("event", "booking-created")
                .field("attendees", 4)
                .field("waitlisted", false)
                .build();
            assertEquals("{\"event\":\"booking-created\",\"attendees\":4,\"waitlisted\":false}", line);
        }

        @Test
        void writesAnEmptyObject() {
            assertEquals("{}", JsonLine.object().build());
        }

        @Test
        void writesNullForAMissingStringValue() {
            assertEquals("{\"name\":null}", JsonLine.object().field("name", (String) null).build());
        }

        @Test
        void skipsOptionalFieldsThatAreMissing() {
            assertEquals("{\"a\":\"1\"}",
                JsonLine.object().field("a", "1").optionalField("b", null).build());
            assertEquals("{\"a\":\"1\",\"b\":\"2\"}",
                JsonLine.object().field("a", "1").optionalField("b", "2").build());
        }

        @Test
        void escapesQuotesAndBackslashes() {
            assertEquals("he said \\\"stop\\\"", JsonLine.escape("he said \"stop\""));
            assertEquals("a\\\\b", JsonLine.escape("a\\b"));
        }

        @Test
        void escapesTheNamedControlCharacters() {
            assertEquals("a\\nb", JsonLine.escape("a\nb"));
            assertEquals("a\\tb", JsonLine.escape("a\tb"));
            assertEquals("a\\rb", JsonLine.escape("a\rb"));
            assertEquals("a\\bb", JsonLine.escape("a\bb"));
            assertEquals("a\\fb", JsonLine.escape("a\fb"));
        }

        @Test
        void escapesOtherControlCharactersNumerically() {
            assertEquals("a\\u0000b", JsonLine.escape("a" + (char) 0x00 + "b"));
            assertEquals("a\\u001fb", JsonLine.escape("a" + (char) 0x1f + "b"));
            assertEquals("a\\u000bb", JsonLine.escape("a" + (char) 0x0b + "b"));
        }

        @Test
        void leavesOrdinaryTextAlone() {
            assertEquals("Sorrells 205", JsonLine.escape("Sorrells 205"));
        }

        @Test
        void escapingIsAppliedToKeysAndValuesAlike() {
            String line = JsonLine.object().field("say \"hi\"", "line\nbreak").build();
            assertEquals("{\"say \\\"hi\\\"\":\"line\\nbreak\"}", line);
        }

        @Test
        void writesNegativeAndLargeNumbers() {
            assertEquals("{\"n\":-7}", JsonLine.object().field("n", -7L).build());
            assertEquals("{\"n\":9007199254740993}",
                JsonLine.object().field("n", 9007199254740993L).build());
        }
    }

    @Nested
    @DisplayName("event log format")
    class Format {

        @Test
        void writesTheCreatedEventFields() {
            assertEquals(
                "{\"event\":\"booking-created\",\"occurredAt\":\"2026-10-19T09:00:00Z\","
                    + "\"bookingId\":\"bk-1\",\"roomId\":\"r-1\",\"userId\":\"u-1\","
                    + "\"start\":\"2026-10-20T10:00:00Z\",\"end\":\"2026-10-20T11:00:00Z\","
                    + "\"attendees\":4}",
                EventLogFormat.toLine(StorageFixtures.created("bk-1")));
        }

        @Test
        void writesTheCancelledEventFields() {
            String line = EventLogFormat.toLine(StorageFixtures.cancelled("bk-1"));
            assertTrue(line.startsWith("{\"event\":\"booking-cancelled\","));
            assertTrue(line.contains("\"bookingId\":\"bk-1\""));
            assertTrue(line.endsWith("\"attendees\":4}"));
        }

        @Test
        void promotionsCarryTheEntryTheyCameFrom() {
            String line = EventLogFormat.toLine(StorageFixtures.promoted("bk-9", "wl-2", 2));
            assertTrue(line.contains("\"event\":\"waitlist-promoted\""));
            assertTrue(line.contains("\"waitlistEntryId\":\"wl-2\""));
            assertTrue(line.contains("\"waitlistSequence\":2"));
        }

        @Test
        void everyLineIsOneLine() {
            for (DomainEvent event : List.of(
                StorageFixtures.created("bk-1"),
                StorageFixtures.cancelled("bk-1"),
                StorageFixtures.promoted("bk-2", "wl-1", 1))) {
                assertFalse(EventLogFormat.toLine(event).contains("\n"));
            }
        }

        @Test
        void declaresTheWireNamesItWrites() {
            List<String> declared = List.of(EventLogFormat.eventTypes());
            assertTrue(declared.contains("booking-created"));
            assertTrue(declared.contains("booking-cancelled"));
            assertTrue(declared.contains("waitlist-promoted"));
            assertTrue(EventLogFormat.isKnownType("booking-created"));
            assertFalse(EventLogFormat.isKnownType("booking-invented"));
        }
    }

    @Nested
    @DisplayName("in-memory event log")
    class Memory {

        @Test
        void keepsEventsInOrder() {
            InMemoryEventLog log = new InMemoryEventLog();
            log.append(StorageFixtures.created("bk-1"));
            log.append(StorageFixtures.cancelled("bk-1"));
            assertEquals(2, log.appended());
            assertEquals(List.of("booking-created", "booking-cancelled"),
                log.events().stream().map(DomainEvent::type).toList());
        }

        @Test
        void filtersByWireName() {
            InMemoryEventLog log = new InMemoryEventLog();
            log.append(StorageFixtures.created("bk-1"));
            log.append(StorageFixtures.created("bk-2"));
            log.append(StorageFixtures.cancelled("bk-1"));
            assertEquals(2, log.ofType("booking-created").size());
            assertTrue(log.ofType("waitlist-promoted").isEmpty());
        }

        @Test
        void writesOneLinePerEvent(@TempDir Path directory) throws IOException {
            InMemoryEventLog log = new InMemoryEventLog();
            log.append(StorageFixtures.created("bk-1"));
            log.append(StorageFixtures.promoted("bk-2", "wl-1", 1));

            Path file = directory.resolve("events.jsonl");
            assertEquals(2, log.writeTo(file));

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).startsWith("{\"event\":\"booking-created\""));
            assertTrue(lines.get(1).contains("\"waitlistEntryId\":\"wl-1\""));
        }

        @Test
        void writingCreatesMissingDirectories(@TempDir Path directory) {
            InMemoryEventLog log = new InMemoryEventLog();
            log.append(StorageFixtures.created("bk-1"));
            Path file = directory.resolve("out").resolve("nested").resolve("events.jsonl");
            assertEquals(1, log.writeTo(file));
            assertTrue(Files.exists(file));
        }

        @Test
        void clearingEmptiesTheLog() {
            InMemoryEventLog log = new InMemoryEventLog();
            log.append(StorageFixtures.created("bk-1"));
            log.clear();
            assertEquals(0, log.appended());
            assertTrue(log.lines().isEmpty());
        }
    }

    @Nested
    @DisplayName("jsonl file writer")
    class FileWriter {

        @Test
        void appendsOneLinePerEvent(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("events.jsonl");
            try (JsonlFileEventLogWriter writer = new JsonlFileEventLogWriter(file)) {
                writer.append(StorageFixtures.created("bk-1"));
                writer.append(StorageFixtures.cancelled("bk-1"));
                writer.flush();
                assertEquals(2, writer.appended());
                assertEquals(file, writer.path());
            }
            assertEquals(2, Files.readAllLines(file, StandardCharsets.UTF_8).size());
        }

        @Test
        void reopeningKeepsWhatWasAlreadyThere(@TempDir Path directory) throws IOException {
            Path file = directory.resolve("events.jsonl");
            try (JsonlFileEventLogWriter first = new JsonlFileEventLogWriter(file)) {
                first.append(StorageFixtures.created("bk-1"));
            }
            try (JsonlFileEventLogWriter second = new JsonlFileEventLogWriter(file)) {
                second.append(StorageFixtures.created("bk-2"));
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("bk-1"));
            assertTrue(lines.get(1).contains("bk-2"));
        }

        @Test
        void createsMissingDirectories(@TempDir Path directory) {
            Path file = directory.resolve("logs").resolve("events.jsonl");
            try (JsonlFileEventLogWriter writer = new JsonlFileEventLogWriter(file)) {
                writer.append(StorageFixtures.created("bk-1"));
            }
            assertTrue(Files.exists(file));
        }

        @Test
        void refusesToAppendAfterClosing(@TempDir Path directory) {
            Path file = directory.resolve("events.jsonl");
            JsonlFileEventLogWriter writer = new JsonlFileEventLogWriter(file);
            writer.close();
            writer.close();
            assertThrows(IllegalStateException.class,
                () -> writer.append(StorageFixtures.created("bk-1")));
        }
    }
}
