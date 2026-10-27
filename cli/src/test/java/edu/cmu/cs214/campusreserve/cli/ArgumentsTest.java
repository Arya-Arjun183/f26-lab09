package edu.cmu.cs214.campusreserve.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ArgumentsTest {

    @Test
    void splitsFlagsSwitchesAndPositionals() {
        Arguments args = Arguments.parse("extra", "--room", "wean-5207", "--include-cancelled");
        assertEquals("wean-5207", args.requireString("room"));
        assertTrue(args.isPresent("include-cancelled"));
        assertFalse(args.isPresent("user"));
        assertEquals("extra", args.positional(0).orElseThrow());
        assertFalse(args.positional(1).isPresent());
    }

    @Test
    void aWordAfterAFlagIsThatFlagsValue() {
        // This is why a switch goes last on a line: the next word would be taken
        // as its value rather than left standing on its own.
        Arguments args = Arguments.parse("--include-cancelled", "extra");
        assertEquals("extra", args.requireString("include-cancelled"));
        assertFalse(args.positional(0).isPresent());
    }

    @Test
    void readsTypedValues() {
        Arguments args = Arguments.parse(
            "--minutes", "90", "--start", "2026-10-20T09:00:00Z", "--date", "2026-10-20");
        assertEquals(90, args.requireCount("minutes"));
        assertEquals(Instant.parse("2026-10-20T09:00:00Z"), args.requireInstant("start"));
        assertEquals(LocalDate.of(2026, 10, 20), args.requireDate("date"));
        assertEquals(1, args.countOrDefault("attendees", 1));
        assertEquals("all", args.stringOrDefault("building", "all"));
    }

    @Test
    void namesTheFlagItIsUnhappyAbout() {
        Arguments given = Arguments.parse("--minutes", "soon", "--start", "yesterday", "--attendees", "0");
        assertTrue(message(() -> given.requireCount("minutes")).contains("--minutes needs a whole number"));
        assertTrue(message(() -> given.requireCount("attendees")).contains("--attendees must be above zero"));
        assertTrue(message(() -> given.requireInstant("start")).contains("--start needs an ISO-8601"));
        assertTrue(message(() -> given.requireDate("start")).contains("YYYY-MM-DD"));
        assertTrue(message(() -> given.requireString("room")).contains("--room is required"));
    }

    @Test
    void refusesUnknownFlagsRepeatedFlagsAndStrayWords() {
        Arguments unknown = Arguments.parse("--romo", "wean-5207");
        assertTrue(message(() -> unknown.allow("room")).contains("Unknown flag --romo"));
        assertThrows(UsageException.class, () -> Arguments.parse("--room", "a", "--room", "b"));
        assertThrows(UsageException.class, () -> Arguments.parse("wean-5207").requireNoPositionals());
        assertThrows(UsageException.class, () -> Arguments.parse("a", "b").requireAtMostPositionals(1));
    }

    private static String message(Runnable call) {
        return assertThrows(UsageException.class, call::run).getMessage();
    }
}
