package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The published prices: an hourly rate for each kind of room, plus the numbers
 * the surcharge and discount rules read.
 *
 * <p>One rate card holds every parameter of the price rules, so a rule object
 * carries no state of its own and the whole price of a booking is a function of
 * the booking and one card. Times in the peak window are minutes since UTC
 * midnight, matching {@link TimeInterval#startMinuteOfDayUtc()}. There is no
 * local time zone in this platform.
 *
 * <p>Every amount on a card shares one currency, and that currency is the one
 * every quote priced from the card comes back in.
 *
 * @param hourlyRates the base rate per hour for each room kind, every kind present
 * @param peakStartMinuteOfDay first minute of the peak window, inclusive
 * @param peakEndMinuteOfDay last minute of the peak window, exclusive
 * @param peakSurchargePercent added to the base charge when a booking starts in the window
 * @param largeGroupThreshold attendee count at which the large group surcharge starts
 * @param largeGroupSurchargePercent added to the base charge for a large group
 * @param longBookingMinutes booked minutes at which the long booking discount starts
 * @param longBookingDiscountPercent taken off the base charge for a long booking
 * @param minimumCharge the floor a quote is topped up to
 */
public record RateCard(
    Map<RoomKind, Money> hourlyRates,
    int peakStartMinuteOfDay,
    int peakEndMinuteOfDay,
    int peakSurchargePercent,
    int largeGroupThreshold,
    int largeGroupSurchargePercent,
    int longBookingMinutes,
    int longBookingDiscountPercent,
    Money minimumCharge) {

    /** Ceiling on a surcharge, high enough for a holiday rate and low enough to catch a typo. */
    private static final int MAX_SURCHARGE_PERCENT = 500;

    /** A discount can take a charge to zero but never below it. */
    private static final int MAX_DISCOUNT_PERCENT = 100;

    public RateCard {
        Objects.requireNonNull(hourlyRates, "hourlyRates");
        Objects.requireNonNull(minimumCharge, "minimumCharge");
        hourlyRates = Map.copyOf(hourlyRates);
        for (RoomKind kind : RoomKind.values()) {
            Money rate = hourlyRates.get(kind);
            if (rate == null) {
                throw new IllegalArgumentException("No hourly rate for " + kind);
            }
            if (rate.isNegative()) {
                throw new IllegalArgumentException("Hourly rate for " + kind + " is negative: " + rate);
            }
            if (!rate.currency().equals(minimumCharge.currency())) {
                throw new IllegalArgumentException(
                    "Rate for " + kind + " is in " + rate.currency()
                        + " but the card is in " + minimumCharge.currency());
            }
        }
        if (peakStartMinuteOfDay < 0 || peakStartMinuteOfDay >= TimeInterval.MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Peak start is outside the day: " + peakStartMinuteOfDay);
        }
        if (peakEndMinuteOfDay <= peakStartMinuteOfDay || peakEndMinuteOfDay > TimeInterval.MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Peak window must be non-empty and inside the day");
        }
        requirePercent(peakSurchargePercent, "Peak surcharge", MAX_SURCHARGE_PERCENT);
        requirePercent(largeGroupSurchargePercent, "Large group surcharge", MAX_SURCHARGE_PERCENT);
        requirePercent(longBookingDiscountPercent, "Long booking discount", MAX_DISCOUNT_PERCENT);
        if (largeGroupThreshold <= 0) {
            throw new IllegalArgumentException("Large group threshold must be positive: " + largeGroupThreshold);
        }
        if (longBookingMinutes <= 0) {
            throw new IllegalArgumentException("Long booking minutes must be positive: " + longBookingMinutes);
        }
        if (minimumCharge.isNegative()) {
            throw new IllegalArgumentException("Minimum charge is negative: " + minimumCharge);
        }
    }

    /**
     * The card the platform bills from: study rooms at USD 5.00 an hour,
     * classrooms at USD 12.00, labs at USD 20.00, auditoriums at USD 60.00. The
     * peak window runs 13:00 to 17:00 UTC and adds 20 percent. A party of 25 or
     * more adds 10 percent, a booking of three hours or more takes 15 percent
     * off, and no quote settles below USD 3.00.
     */
    public static RateCard standard() {
        Map<RoomKind, Money> rates = new EnumMap<>(RoomKind.class);
        rates.put(RoomKind.STUDY_ROOM, Money.usd(500));
        rates.put(RoomKind.CLASSROOM, Money.usd(1_200));
        rates.put(RoomKind.LAB, Money.usd(2_000));
        rates.put(RoomKind.AUDITORIUM, Money.usd(6_000));
        return new RateCard(rates, 13 * 60, 17 * 60, 20, 25, 10, 180, 15, Money.usd(300));
    }

    /** The hourly rate for a kind of room. */
    public Money hourlyRate(RoomKind kind) {
        Money rate = hourlyRates.get(Objects.requireNonNull(kind, "kind"));
        if (rate == null) {
            throw new IllegalStateException("No hourly rate for " + kind);
        }
        return rate;
    }

    /** The currency every amount on this card is quoted in. */
    public String currency() {
        return minimumCharge.currency();
    }

    /** Nothing at all in this card's currency, the starting point for a running total. */
    public Money zero() {
        return Money.zero(currency());
    }

    /**
     * Does a booking that starts at this minute of the UTC day start inside the
     * peak window? The window is half open, so a booking starting exactly at the
     * closing minute is off peak.
     */
    public boolean isPeakStart(int minuteOfDayUtc) {
        return minuteOfDayUtc >= peakStartMinuteOfDay && minuteOfDayUtc < peakEndMinuteOfDay;
    }

    /** A copy carrying a different hourly rate for one kind of room. */
    public RateCard withHourlyRate(RoomKind kind, Money rate) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(rate, "rate");
        Map<RoomKind, Money> rates = new EnumMap<>(hourlyRates);
        rates.put(kind, rate);
        return new RateCard(
            rates, peakStartMinuteOfDay, peakEndMinuteOfDay, peakSurchargePercent,
            largeGroupThreshold, largeGroupSurchargePercent,
            longBookingMinutes, longBookingDiscountPercent, minimumCharge);
    }

    /** A copy with a different peak window, in minutes since UTC midnight. */
    public RateCard withPeakWindow(int startMinuteOfDay, int endMinuteOfDay) {
        return new RateCard(
            hourlyRates, startMinuteOfDay, endMinuteOfDay, peakSurchargePercent,
            largeGroupThreshold, largeGroupSurchargePercent,
            longBookingMinutes, longBookingDiscountPercent, minimumCharge);
    }

    /** A copy with a different peak surcharge. Zero turns the surcharge off. */
    public RateCard withPeakSurchargePercent(int percent) {
        return new RateCard(
            hourlyRates, peakStartMinuteOfDay, peakEndMinuteOfDay, percent,
            largeGroupThreshold, largeGroupSurchargePercent,
            longBookingMinutes, longBookingDiscountPercent, minimumCharge);
    }

    /** A copy with a different large group rule. Zero percent turns it off. */
    public RateCard withLargeGroup(int threshold, int percent) {
        return new RateCard(
            hourlyRates, peakStartMinuteOfDay, peakEndMinuteOfDay, peakSurchargePercent,
            threshold, percent,
            longBookingMinutes, longBookingDiscountPercent, minimumCharge);
    }

    /** A copy with a different long booking rule. Zero percent turns it off. */
    public RateCard withLongBooking(int minutes, int percent) {
        return new RateCard(
            hourlyRates, peakStartMinuteOfDay, peakEndMinuteOfDay, peakSurchargePercent,
            largeGroupThreshold, largeGroupSurchargePercent,
            minutes, percent, minimumCharge);
    }

    /** A copy with a different floor. The amount must be in the card's currency. */
    public RateCard withMinimumCharge(Money floor) {
        return new RateCard(
            hourlyRates, peakStartMinuteOfDay, peakEndMinuteOfDay, peakSurchargePercent,
            largeGroupThreshold, largeGroupSurchargePercent,
            longBookingMinutes, longBookingDiscountPercent, floor);
    }

    private static void requirePercent(int value, String field, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(field + " must be between 0 and " + max + ": " + value);
        }
    }
}
