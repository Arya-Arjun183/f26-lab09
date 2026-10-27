package edu.cmu.cs214.campusreserve.notifications;

import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Writes times out for a person to read.
 *
 * <p>Everything in the platform is UTC, so every string produced here says UTC.
 * Nothing guesses at a reader's local time zone: a message that shifted times
 * would disagree with the booking page.
 */
public final class IntervalText {

    private static final DateTimeFormatter DATE =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private IntervalText() {
    }

    /**
     * An interval as one line, for example
     * {@code 2026-10-20 10:00 to 11:00 UTC}. An interval that crosses midnight
     * repeats the date on the end, for example
     * {@code 2026-10-20 23:00 to 2026-10-21 01:00 UTC}.
     */
    public static String formatUtc(TimeInterval interval) {
        Objects.requireNonNull(interval, "interval");
        Instant start = interval.start();
        Instant end = interval.end();
        LocalDate startDate = LocalDate.ofInstant(start, ZoneOffset.UTC);
        LocalDate endDate = LocalDate.ofInstant(end, ZoneOffset.UTC);

        String head = DATE.format(start) + " " + TIME.format(start);
        String tail = startDate.equals(endDate)
            ? TIME.format(end)
            : DATE.format(end) + " " + TIME.format(end);
        return head + " to " + tail + " UTC";
    }
}
