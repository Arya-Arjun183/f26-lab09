package edu.cmu.cs214.campusreserve.accounts;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where token strings come from. Pulling this out of the token service is what
 * lets a test predict the exact value a call will hand back, since the running
 * platform mints values no test could guess.
 */
@FunctionalInterface
public interface TokenMinter {

    /** How many random bytes the default minter draws per token. */
    int RANDOM_BYTES = 32;

    /**
     * A fresh opaque string. The account and the issue time are passed in for
     * minters that want to fold them in; a value must never be reused, and a
     * value must not contain whitespace.
     */
    String mint(String userId, Instant issuedAt);

    /**
     * The minter for the running platform: 32 bytes out of {@link SecureRandom},
     * base64-url encoded without padding, ignoring both arguments so that the
     * value gives away nothing about who holds it or when it was made.
     */
    static TokenMinter random() {
        return random(RANDOM_BYTES);
    }

    /** The random minter with a chosen number of bytes, which must be at least 16. */
    static TokenMinter random(int byteCount) {
        if (byteCount < 16) {
            throw new IllegalArgumentException("A token needs at least 16 random bytes: " + byteCount);
        }
        SecureRandom source = new SecureRandom();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return (userId, issuedAt) -> {
            byte[] bytes = new byte[byteCount];
            source.nextBytes(bytes);
            return encoder.encodeToString(bytes);
        };
    }

    /**
     * A counting minter for tests: {@code prefix-1}, {@code prefix-2}, and so on.
     * Each returned minter counts on its own, so give two minters feeding the
     * same service different prefixes.
     */
    static TokenMinter sequential(String prefix) {
        String checked = Identifiers.require(prefix, "token prefix");
        AtomicLong counter = new AtomicLong();
        return (userId, issuedAt) -> checked + "-" + counter.incrementAndGet();
    }
}
