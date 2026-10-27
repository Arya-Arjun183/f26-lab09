package edu.cmu.cs214.campusreserve.billing;

import java.util.Objects;

/**
 * An amount of money, held as a whole number of cents in one currency.
 *
 * <p>The amount is a {@code long} count of cents. Nothing in this type converts
 * to {@code double} or {@code float}, so a price assembled from several rules
 * lands on the same cent every time it is computed.
 *
 * <p>Every arithmetic and comparison method refuses to mix currencies. Adding
 * USD to EUR throws instead of producing a number nobody can read.
 *
 * @param cents the amount in cents, which may be negative for a credit
 * @param currency the ISO-style currency code, three uppercase letters
 */
public record Money(long cents, String currency) implements Comparable<Money> {

    /** Cents in one unit of currency. */
    private static final int CENTS_PER_UNIT = 100;

    public Money {
        currency = requireCurrency(currency);
    }

    /** An amount in the given currency, for example {@code of("USD", 1250)}. */
    public static Money of(String currency, long cents) {
        return new Money(cents, currency);
    }

    /** An amount in US dollars, given in cents. */
    public static Money usd(long cents) {
        return new Money(cents, "USD");
    }

    /** Nothing at all, in the given currency. */
    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    /** Is the amount exactly zero? */
    public boolean isZero() {
        return cents == 0L;
    }

    /** Is the amount above zero? */
    public boolean isPositive() {
        return cents > 0L;
    }

    /** Is the amount below zero, that is, a credit? */
    public boolean isNegative() {
        return cents < 0L;
    }

    /** This amount plus {@code other}, which must carry the same currency. */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(cents, other.cents), currency);
    }

    /** This amount less {@code other}, which must carry the same currency. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(cents, other.cents), currency);
    }

    /** This amount repeated {@code factor} times. */
    public Money times(int factor) {
        return new Money(Math.multiplyExact(cents, (long) factor), currency);
    }

    /** This amount with its sign flipped. */
    public Money negated() {
        return new Money(Math.negateExact(cents), currency);
    }

    /**
     * The given percentage of this amount, rounded half up to the cent.
     *
     * <p>Half up here means half away from zero, so 50 percent of USD 1.01 is
     * USD 0.51 and 50 percent of USD -1.01 is USD -0.51. Surcharges and discounts
     * therefore round by the same rule whichever direction they run.
     *
     * @param percent a whole percentage, at least zero
     */
    public Money percentage(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("Percentage must not be negative: " + percent);
        }
        return prorate(percent, CENTS_PER_UNIT);
    }

    /**
     * The fraction {@code numerator / denominator} of this amount, rounded half
     * up to the cent. Billing an hourly rate by the minute goes through here with
     * the minute count over 60.
     *
     * @param numerator the part being charged, at least zero
     * @param denominator the whole it is measured against, above zero
     */
    public Money prorate(long numerator, long denominator) {
        if (numerator < 0L) {
            throw new IllegalArgumentException("Numerator must not be negative: " + numerator);
        }
        if (denominator <= 0L) {
            throw new IllegalArgumentException("Denominator must be positive: " + denominator);
        }
        long scaled = Math.multiplyExact(cents, numerator);
        long whole = scaled / denominator;
        long remainder = Math.abs(scaled % denominator);
        if (remainder * 2 >= denominator) {
            whole += scaled < 0L ? -1L : 1L;
        }
        return new Money(whole, currency);
    }

    /** Is this amount below {@code other}? */
    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /** Is this amount above {@code other}? */
    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    /** Orders two amounts in the same currency by their cent counts. */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(cents, other.cents);
    }

    /**
     * The amount as currency code, a space, and two decimal places, for example
     * {@code USD 12.50}. A negative amount keeps its sign in front of the digits:
     * {@code USD -12.50}.
     */
    public String format() {
        long units = cents / CENTS_PER_UNIT;
        long fraction = Math.abs(cents % CENTS_PER_UNIT);
        String sign = cents < 0L && units == 0L ? "-" : "";
        return currency + " " + sign + units + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    /** Same text as {@link #format()}, so failures read in plain money. */
    @Override
    public String toString() {
        return format();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currencies do not match: " + currency + " and " + other.currency);
        }
    }

    private static String requireCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() != 3) {
            throw new IllegalArgumentException("Currency must be three letters: " + value);
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException("Currency must be uppercase letters: " + value);
            }
        }
        return trimmed;
    }
}
