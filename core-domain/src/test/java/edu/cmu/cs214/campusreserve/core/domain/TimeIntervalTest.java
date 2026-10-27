package edu.cmu.cs214.campusreserve.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimeIntervalTest {

    private static TimeInterval at(String start, String end) {
        return TimeInterval.parse("2026-10-20T" + start + ":00Z", "2026-10-20T" + end + ":00Z");
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void rejectsEmptyInterval() {
            Instant noon = Instant.parse("2026-10-20T12:00:00Z");
            assertThrows(IllegalArgumentException.class, () -> TimeInterval.of(noon, noon));
        }

        @Test
        void rejectsReversedInterval() {
            assertThrows(IllegalArgumentException.class, () -> at("12:00", "11:00"));
        }

        @Test
        void rejectsSecondsOnEndpoints() {
            Instant start = Instant.parse("2026-10-20T12:00:30Z");
            Instant end = Instant.parse("2026-10-20T13:00:00Z");
            assertThrows(IllegalArgumentException.class, () -> TimeInterval.of(start, end));
        }

        @Test
        void rejectsNanosecondsOnEndpoints() {
            Instant start = Instant.parse("2026-10-20T12:00:00Z");
            Instant end = Instant.parse("2026-10-20T13:00:00.5Z");
            assertThrows(IllegalArgumentException.class, () -> TimeInterval.of(start, end));
        }

        @Test
        void ofMinutesBuildsFromDuration() {
            TimeInterval interval = TimeInterval.ofMinutes(Instant.parse("2026-10-20T12:00:00Z"), 90);
            assertEquals(Instant.parse("2026-10-20T13:30:00Z"), interval.end());
        }

        @Test
        void ofMinutesRejectsNonPositiveDuration() {
            Instant noon = Instant.parse("2026-10-20T12:00:00Z");
            assertThrows(IllegalArgumentException.class, () -> TimeInterval.ofMinutes(noon, 0));
        }
    }

    @Nested
    @DisplayName("overlaps")
    class Overlaps {

        @Test
        void identicalIntervalsOverlap() {
            assertTrue(at("10:00", "11:00").overlaps(at("10:00", "11:00")));
        }

        @Test
        void partialOverlapCountsBothWays() {
            TimeInterval early = at("10:00", "11:00");
            TimeInterval late = at("10:30", "11:30");
            assertTrue(early.overlaps(late));
            assertTrue(late.overlaps(early));
        }

        @Test
        void containedIntervalOverlaps() {
            assertTrue(at("10:00", "12:00").overlaps(at("10:30", "11:00")));
            assertTrue(at("10:30", "11:00").overlaps(at("10:00", "12:00")));
        }

        @Test
        void backToBackIntervalsDoNotOverlap() {
            assertFalse(at("10:00", "11:00").overlaps(at("11:00", "12:00")));
            assertFalse(at("11:00", "12:00").overlaps(at("10:00", "11:00")));
        }

        @Test
        void disjointIntervalsDoNotOverlap() {
            assertFalse(at("09:00", "10:00").overlaps(at("11:00", "12:00")));
        }

        @Test
        void oneMinuteOfSharedTimeIsAnOverlap() {
            assertTrue(at("10:00", "11:00").overlaps(at("10:59", "11:30")));
        }
    }

    @Nested
    @DisplayName("membership")
    class Membership {

        @Test
        void containsTheStartButNotTheEnd() {
            TimeInterval interval = at("10:00", "11:00");
            assertTrue(interval.contains(Instant.parse("2026-10-20T10:00:00Z")));
            assertTrue(interval.contains(Instant.parse("2026-10-20T10:59:00Z")));
            assertFalse(interval.contains(Instant.parse("2026-10-20T11:00:00Z")));
            assertFalse(interval.contains(Instant.parse("2026-10-20T09:59:00Z")));
        }

        @Test
        void touchesReportsAdjacency() {
            assertTrue(at("10:00", "11:00").touches(at("11:00", "12:00")));
            assertTrue(at("11:00", "12:00").touches(at("10:00", "11:00")));
            assertFalse(at("10:00", "11:00").touches(at("11:30", "12:00")));
        }

        @Test
        void isWithinChecksContainment() {
            assertTrue(at("10:00", "11:00").isWithin(at("09:00", "12:00")));
            assertTrue(at("09:00", "12:00").isWithin(at("09:00", "12:00")));
            assertFalse(at("08:00", "11:00").isWithin(at("09:00", "12:00")));
        }
    }

    @Nested
    @DisplayName("calendar arithmetic")
    class CalendarArithmetic {

        @Test
        void durationCountsWholeMinutes() {
            assertEquals(90, at("10:00", "11:30").durationMinutes());
        }

        @Test
        void reportsMinutesOfDayInUtc() {
            TimeInterval interval = at("06:15", "07:45");
            assertEquals(375, interval.startMinuteOfDayUtc());
            assertEquals(465, interval.endMinuteOfDayUtc());
        }

        @Test
        void intervalEndingAtMidnightReportsFullDay() {
            TimeInterval interval = TimeInterval.parse("2026-10-20T23:00:00Z", "2026-10-21T00:00:00Z");
            assertEquals(TimeInterval.MINUTES_PER_DAY, interval.endMinuteOfDayUtc());
            assertTrue(interval.isWithinOneUtcDay());
        }

        @Test
        void intervalCrossingMidnightSpansTwoDays() {
            TimeInterval interval = TimeInterval.parse("2026-10-20T23:00:00Z", "2026-10-21T01:00:00Z");
            assertFalse(interval.isWithinOneUtcDay());
        }

        @Test
        void reportsStartDateInUtc() {
            assertEquals(LocalDate.of(2026, 10, 20), at("10:00", "11:00").startDateUtc());
        }

        @Test
        void alignmentChecksBothEndpoints() {
            assertTrue(at("10:00", "11:30").isAlignedTo(15));
            assertTrue(at("10:15", "10:45").isAlignedTo(15));
            assertFalse(at("10:10", "11:00").isAlignedTo(15));
            assertFalse(at("10:00", "11:10").isAlignedTo(15));
        }

        @Test
        void rejectsNonPositiveAlignment() {
            TimeInterval interval = at("10:00", "11:00");
            assertThrows(IllegalArgumentException.class, () -> interval.isAlignedTo(0));
        }

        @Test
        void reportsWhetherItHasFinished() {
            TimeInterval interval = at("10:00", "11:00");
            assertTrue(interval.endsOnOrBefore(Instant.parse("2026-10-20T11:00:00Z")));
            assertFalse(interval.endsOnOrBefore(Instant.parse("2026-10-20T10:59:00Z")));
            assertTrue(interval.startsOnOrAfter(Instant.parse("2026-10-20T10:00:00Z")));
            assertFalse(interval.startsOnOrAfter(Instant.parse("2026-10-20T10:01:00Z")));
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        void sortsByStartThenEnd() {
            List<TimeInterval> intervals = new ArrayList<>(List.of(
                at("11:00", "12:00"),
                at("10:00", "12:00"),
                at("10:00", "11:00")));
            intervals.sort(null);
            assertEquals(List.of(at("10:00", "11:00"), at("10:00", "12:00"), at("11:00", "12:00")), intervals);
        }

        @Test
        void printsHalfOpenNotation() {
            assertEquals("[2026-10-20T10:00:00Z, 2026-10-20T11:00:00Z)", at("10:00", "11:00").toString());
        }
    }
}
