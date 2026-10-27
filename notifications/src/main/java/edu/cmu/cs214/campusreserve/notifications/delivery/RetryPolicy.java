package edu.cmu.cs214.campusreserve.notifications.delivery;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * How many times to try a delivery, and which failures are worth trying again.
 *
 * <p>There is no backoff and no waiting here. Attempts happen back to back on
 * the calling thread, which keeps a dispatch run predictable and keeps this
 * class free of threads and timers. A deployment that wants a pause between
 * attempts puts one in its scheduler, between dispatch runs.
 *
 * @param maxAttempts how many times to try in total, at least once
 * @param retryable decides, from the failure, whether another attempt is worth
 *     making
 */
public record RetryPolicy(int maxAttempts, Predicate<Throwable> retryable) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Attempts must be at least 1: " + maxAttempts);
        }
        Objects.requireNonNull(retryable, "retryable");
    }

    /** One attempt, no second chance. */
    public static RetryPolicy none() {
        return new RetryPolicy(1, failure -> false);
    }

    /**
     * Up to {@code maxAttempts} tries, retrying transport failures the transport
     * itself called temporary. Anything else stops on the first failure.
     */
    public static RetryPolicy transientFailures(int maxAttempts) {
        return new RetryPolicy(maxAttempts,
            failure -> failure instanceof TransportException transport && !transport.isPermanent());
    }

    /**
     * Should the caller try again?
     *
     * @param attemptsSoFar how many attempts have already been made, at least one
     * @param failure what the last attempt threw
     */
    public boolean shouldRetry(int attemptsSoFar, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (attemptsSoFar < 1) {
            throw new IllegalArgumentException("Attempts so far must be at least 1: " + attemptsSoFar);
        }
        return attemptsSoFar < maxAttempts && retryable.test(failure);
    }
}
