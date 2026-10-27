package edu.cmu.cs214.campusreserve.accounts;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An opaque string a caller presents to prove which account it is acting as,
 * together with the window it is good for. The record carries no secret beyond
 * the value itself: everything else about the account is looked up by id.
 *
 * @param value the opaque string the caller sends back
 * @param userId the account the token speaks for
 * @param issuedAt when the token was minted
 * @param expiresAt the first instant the token no longer works
 */
public record Token(String value, String userId, Instant issuedAt, Instant expiresAt) {

    private static final int VISIBLE_PREFIX = 4;

    public Token {
        value = Identifiers.require(value, "token value");
        userId = Identifiers.require(userId, "user id");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Token expiry must be after its issue time: " + expiresAt);
        }
    }

    /**
     * Is the token past its window at that instant? The expiry instant itself
     * counts as expired, so a token issued for one hour stops working exactly one
     * hour later and not a tick after.
     */
    public boolean isExpiredAt(Instant when) {
        Objects.requireNonNull(when, "when");
        return !when.isBefore(expiresAt);
    }

    /**
     * How many whole seconds of the window are left at that instant, or zero once
     * it has run out. Partial seconds are dropped, so a token with 900 more
     * milliseconds reports zero.
     */
    public long remainingSeconds(Instant when) {
        if (isExpiredAt(when)) {
            return 0L;
        }
        return Duration.between(when, expiresAt).toSeconds();
    }

    /** The whole window the token was issued for. */
    public Duration lifetime() {
        return Duration.between(issuedAt, expiresAt);
    }

    /**
     * The value with all but its first few characters replaced by dots. Denial
     * messages and logs use this so that a live token never lands in a log file.
     */
    public String maskedValue() {
        return mask(value);
    }

    /** The masking rule, also used for values that were never minted here. */
    public static String mask(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return "...";
        }
        String trimmed = tokenValue.trim();
        if (trimmed.length() <= VISIBLE_PREFIX) {
            return "...";
        }
        return trimmed.substring(0, VISIBLE_PREFIX) + "...";
    }
}
