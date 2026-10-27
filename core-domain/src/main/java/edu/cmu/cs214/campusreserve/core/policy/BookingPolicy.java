package edu.cmu.cs214.campusreserve.core.policy;

/**
 * The platform-wide booking limits. Rooms narrow some of these further through
 * their {@link edu.cmu.cs214.campusreserve.core.domain.RoomKind}; the effective
 * limit is always the stricter of the two.
 *
 * <p>Minute-of-day fields are UTC, counted from midnight. The default opening
 * window runs 06:00 to 22:00 UTC.
 */
public record BookingPolicy(
    int minDurationMinutes,
    int maxDurationMinutes,
    int slotGranularityMinutes,
    int maxAdvanceDays,
    int openingMinuteOfDayUtc,
    int closingMinuteOfDayUtc,
    int maxActiveBookingsPerUser,
    boolean waitlistEnabled) {

    public BookingPolicy {
        requirePositive(minDurationMinutes, "minDurationMinutes");
        requirePositive(maxDurationMinutes, "maxDurationMinutes");
        requirePositive(slotGranularityMinutes, "slotGranularityMinutes");
        requirePositive(maxAdvanceDays, "maxAdvanceDays");
        requirePositive(maxActiveBookingsPerUser, "maxActiveBookingsPerUser");
        if (minDurationMinutes > maxDurationMinutes) {
            throw new IllegalArgumentException(
                "Minimum duration exceeds maximum: " + minDurationMinutes + " over " + maxDurationMinutes);
        }
        if (minDurationMinutes % slotGranularityMinutes != 0) {
            throw new IllegalArgumentException(
                "Minimum duration must be a whole number of slots: " + minDurationMinutes);
        }
        requireMinuteOfDay(openingMinuteOfDayUtc, "openingMinuteOfDayUtc");
        requireMinuteOfDay(closingMinuteOfDayUtc, "closingMinuteOfDayUtc");
        if (openingMinuteOfDayUtc >= closingMinuteOfDayUtc) {
            throw new IllegalArgumentException(
                "Opening minute must precede closing minute: "
                    + openingMinuteOfDayUtc + " to " + closingMinuteOfDayUtc);
        }
    }

    /** The limits the platform ships with. */
    public static BookingPolicy defaults() {
        return new BookingPolicy(30, 240, 15, 60, 6 * 60, 22 * 60, 5, true);
    }

    /** A copy with the waitlist turned on or off. */
    public BookingPolicy withWaitlistEnabled(boolean enabled) {
        return new BookingPolicy(
            minDurationMinutes,
            maxDurationMinutes,
            slotGranularityMinutes,
            maxAdvanceDays,
            openingMinuteOfDayUtc,
            closingMinuteOfDayUtc,
            maxActiveBookingsPerUser,
            enabled);
    }

    /** A copy with a different per-user booking cap. */
    public BookingPolicy withMaxActiveBookingsPerUser(int cap) {
        return new BookingPolicy(
            minDurationMinutes,
            maxDurationMinutes,
            slotGranularityMinutes,
            maxAdvanceDays,
            openingMinuteOfDayUtc,
            closingMinuteOfDayUtc,
            cap,
            waitlistEnabled);
    }

    /** A copy with a different opening window. */
    public BookingPolicy withOpeningWindow(int openingMinute, int closingMinute) {
        return new BookingPolicy(
            minDurationMinutes,
            maxDurationMinutes,
            slotGranularityMinutes,
            maxAdvanceDays,
            openingMinute,
            closingMinute,
            maxActiveBookingsPerUser,
            waitlistEnabled);
    }

    /** How many minutes a room is open each day under this policy. */
    public int openMinutesPerDay() {
        return closingMinuteOfDayUtc - openingMinuteOfDayUtc;
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
    }

    private static void requireMinuteOfDay(int value, String field) {
        if (value < 0 || value > 24 * 60) {
            throw new IllegalArgumentException(field + " must be a minute of the day: " + value);
        }
    }
}
