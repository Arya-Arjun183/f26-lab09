package edu.cmu.cs214.campusreserve.cli;

import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The wording the commands share: times, counts, and the label column of a
 * details listing.
 *
 * <p>Everything in the platform is UTC, so nothing here takes a zone and nothing
 * converts.
 */
public final class Format {

    /** Width of the label column in a details listing. */
    private static final int LABEL_WIDTH = 11;

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_AND_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private Format() {
    }

    /** The UTC time of day, written {@code 09:00}. */
    public static String clockTime(Instant instant) {
        return TIME.format(instant);
    }

    /** The UTC date and time, written {@code 2026-10-20 09:00}. */
    public static String dateAndTime(Instant instant) {
        return DATE_AND_TIME.format(instant);
    }

    /**
     * An interval in one phrase. An interval inside one day names the day once,
     * for example {@code 2026-10-20 09:00 to 10:30}.
     */
    public static String span(TimeInterval interval) {
        Instant end = interval.end();
        return dateAndTime(interval.start()) + " to "
            + (interval.isWithinOneUtcDay() ? clockTime(end) : dateAndTime(end));
    }

    /**
     * A count and the thing counted, with the plural {@code s} added when it is
     * wanted: {@code 1 room}, {@code 8 rooms}, {@code 0 rooms}.
     */
    public static String count(long amount, String singular) {
        return count(amount, singular, singular + "s");
    }

    /** A count and the thing counted, for a word whose plural is not the {@code s} form. */
    public static String count(long amount, String singular, String plural) {
        return amount + " " + (amount == 1 ? singular : plural);
    }

    /** One line of a details listing: a label in a fixed column, then the value. */
    public static String field(String label, String value) {
        return String.format("%-" + LABEL_WIDTH + "s%s", label, value);
    }
}
