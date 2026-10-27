package edu.cmu.cs214.campusreserve.accounts;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The checks the account types share. Email validation and header parsing live
 * here so that the record, the directory, and the authorization service all
 * accept and reject exactly the same strings.
 */
public final class AccountRules {

    /** The only authentication scheme this platform reads. */
    public static final String BEARER_SCHEME = "Bearer";

    /** The longest lifetime a token may be issued for: thirty days. */
    public static final Duration MAX_TOKEN_LIFETIME = Duration.ofDays(30);

    private AccountRules() {
    }

    /**
     * Validates an address and returns it trimmed, with its original case kept.
     * The rules match the ones the booking domain applies to its own user view,
     * plus a rejection of embedded whitespace: exactly one at sign, something on
     * either side of it, and a dot somewhere in the domain.
     */
    public static String requireEmail(String value) {
        String trimmed = Identifiers.requireText(value, "email");
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                throw new IllegalArgumentException("Email must not contain whitespace: " + value);
            }
        }
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            throw new IllegalArgumentException("Email must contain one at sign: " + value);
        }
        if (trimmed.indexOf('.', at) < 0) {
            throw new IllegalArgumentException("Email domain must contain a dot: " + value);
        }
        return trimmed;
    }

    /**
     * The lookup form of an address: trimmed and lower cased. Two addresses that
     * differ only in case are the same person, so every index key goes through
     * this method.
     */
    public static String normalizeEmail(String value) {
        return requireEmail(value).toLowerCase(Locale.ROOT);
    }

    /** Validates a token lifetime: positive and no longer than thirty days. */
    public static Duration requireLifetime(Duration lifetime) {
        if (lifetime == null) {
            throw new IllegalArgumentException("token lifetime is required");
        }
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("Token lifetime must be positive: " + lifetime);
        }
        if (lifetime.compareTo(MAX_TOKEN_LIFETIME) > 0) {
            throw new IllegalArgumentException("Token lifetime is longer than " + MAX_TOKEN_LIFETIME);
        }
        return lifetime;
    }

    /**
     * Pulls the token out of an {@code Authorization} header value. A null or
     * blank value means the caller sent no header, which is reported as an empty
     * result rather than a failure. Extra whitespace around and inside the value
     * is tolerated, so {@code "  Bearer   abc123 "} yields {@code "abc123"}.
     *
     * @throws IllegalArgumentException if the scheme is not Bearer, or the scheme
     *     is present with no token after it
     */
    public static Optional<String> bearerToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        String[] parts = headerValue.trim().split("\\s+");
        if (!BEARER_SCHEME.equalsIgnoreCase(parts[0])) {
            throw new IllegalArgumentException("Authorization scheme must be " + BEARER_SCHEME);
        }
        if (parts.length == 1) {
            throw new IllegalArgumentException("Authorization header carries no token value");
        }
        if (parts.length > 2) {
            throw new IllegalArgumentException("Authorization header carries more than one token value");
        }
        return Optional.of(parts[1]);
    }

    /** Builds the header value a client should send for a token. */
    public static String authorizationHeader(String tokenValue) {
        return BEARER_SCHEME + " " + Identifiers.require(tokenValue, "token value");
    }
}
