package edu.cmu.cs214.campusreserve.accounts;

import java.time.Instant;
import java.util.Objects;

/**
 * One live sign-in, described in a form that is safe to show to the person who
 * holds it. The token value never appears in full: someone reading a session
 * list over a shoulder should not be able to walk away with a working
 * credential.
 *
 * @param userId the account the session belongs to
 * @param maskedToken the first few characters of the token value, then dots
 * @param issuedAt when the session started
 * @param expiresAt when the session stops working
 * @param remainingSeconds whole seconds left at the moment the summary was taken
 */
public record SessionSummary(String userId, String maskedToken, Instant issuedAt, Instant expiresAt,
                             long remainingSeconds) {

    public SessionSummary {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(maskedToken, "maskedToken");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (remainingSeconds < 0) {
            throw new IllegalArgumentException("remainingSeconds must not be negative: " + remainingSeconds);
        }
    }

    /** The summary of a token as of that instant. */
    public static SessionSummary of(Token token, Instant now) {
        Objects.requireNonNull(token, "token");
        return new SessionSummary(token.userId(), token.maskedValue(), token.issuedAt(), token.expiresAt(),
            token.remainingSeconds(now));
    }

    /** Was the session still usable at the moment the summary was taken? */
    public boolean live() {
        return remainingSeconds > 0;
    }
}
