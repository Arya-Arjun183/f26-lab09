package edu.cmu.cs214.campusreserve.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What came of a cancellation. A successful cancellation reports the bookings
 * that were promoted off the waitlist into the time it freed.
 */
public sealed interface CancellationResult {

    /** One waitlist entry that turned into a booking. */
    record Promotion(WaitlistEntry entry, Booking booking) {
        public Promotion {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(booking, "booking");
        }
    }

    /** The booking is cancelled. {@code promotions} may be empty. */
    record Cancelled(Booking booking, List<Promotion> promotions) implements CancellationResult {
        public Cancelled {
            Objects.requireNonNull(booking, "booking");
            promotions = List.copyOf(promotions);
        }
    }

    /** The cancellation was refused. Nothing changed. */
    record Rejected(RejectionReason reason, String detail) implements CancellationResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.message() : detail;
        }
    }

    /** Builds a rejection carrying the reason's own message. */
    static CancellationResult rejected(RejectionReason reason) {
        return new Rejected(reason, reason.message());
    }

    /** Did the cancellation go through? */
    default boolean isCancelled() {
        return this instanceof Cancelled;
    }

    /** The cancelled booking, when the cancellation went through. */
    default Optional<Booking> cancelledBooking() {
        return this instanceof Cancelled cancelled ? Optional.of(cancelled.booking()) : Optional.empty();
    }

    /** Promotions triggered by the cancellation, empty when it was refused. */
    default List<Promotion> promotions() {
        return this instanceof Cancelled cancelled ? cancelled.promotions() : List.of();
    }

    /** The reason, when the cancellation was refused. */
    default Optional<RejectionReason> rejectionReason() {
        return this instanceof Rejected rejected ? Optional.of(rejected.reason()) : Optional.empty();
    }
}
