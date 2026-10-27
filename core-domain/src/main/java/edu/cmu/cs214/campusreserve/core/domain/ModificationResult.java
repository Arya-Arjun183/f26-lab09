package edu.cmu.cs214.campusreserve.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What came of a change to a booking. A successful change reports the bookings
 * that were promoted off the waitlist into the time it freed, in the same shape
 * a cancellation reports them.
 */
public sealed interface ModificationResult {

    /** The booking now holds the new interval. {@code promotions} may be empty. */
    record Modified(Booking booking, List<CancellationResult.Promotion> promotions)
        implements ModificationResult {
        public Modified {
            Objects.requireNonNull(booking, "booking");
            promotions = List.copyOf(promotions);
        }
    }

    /** The change was refused. The booking stands exactly as it was. */
    record Rejected(RejectionReason reason, String detail) implements ModificationResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.message() : detail;
        }
    }

    /** Builds a rejection carrying the reason's own message. */
    static ModificationResult rejected(RejectionReason reason) {
        return new Rejected(reason, reason.message());
    }

    /** Did the change go through? */
    default boolean isModified() {
        return this instanceof Modified;
    }

    /** The booking as it now stands, when the change went through. */
    default Optional<Booking> modifiedBooking() {
        return this instanceof Modified modified ? Optional.of(modified.booking()) : Optional.empty();
    }

    /** Promotions triggered by the change, empty when it was refused. */
    default List<CancellationResult.Promotion> promotions() {
        return this instanceof Modified modified ? modified.promotions() : List.of();
    }

    /** The reason, when the change was refused. */
    default Optional<RejectionReason> rejectionReason() {
        return this instanceof Rejected rejected ? Optional.of(rejected.reason()) : Optional.empty();
    }
}
