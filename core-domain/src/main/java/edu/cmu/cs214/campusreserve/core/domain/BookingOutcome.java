package edu.cmu.cs214.campusreserve.core.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What came of a booking request: the room was held, the asker joined the
 * waitlist, or the request was refused.
 */
public sealed interface BookingOutcome {

    /** The room is held for the asker. */
    record Confirmed(Booking booking) implements BookingOutcome {
        public Confirmed {
            Objects.requireNonNull(booking, "booking");
        }
    }

    /** The slot was taken, so the asker joined the room's waitlist. */
    record Waitlisted(WaitlistEntry entry) implements BookingOutcome {
        public Waitlisted {
            Objects.requireNonNull(entry, "entry");
        }
    }

    /** The request broke a rule. Nothing was stored. */
    record Rejected(RejectionReason reason, String detail) implements BookingOutcome {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.message() : detail;
        }
    }

    /** Builds a rejection carrying the reason's own message. */
    static BookingOutcome rejected(RejectionReason reason) {
        return new Rejected(reason, reason.message());
    }

    /** Did the asker get the room? */
    default boolean isConfirmed() {
        return this instanceof Confirmed;
    }

    /** Did the asker land on the waitlist? */
    default boolean isWaitlisted() {
        return this instanceof Waitlisted;
    }

    /** Was the request refused? */
    default boolean isRejected() {
        return this instanceof Rejected;
    }

    /** The booking, when the outcome is a confirmation. */
    default Optional<Booking> confirmedBooking() {
        return this instanceof Confirmed confirmed ? Optional.of(confirmed.booking()) : Optional.empty();
    }

    /** The waitlist entry, when the outcome is a waitlisting. */
    default Optional<WaitlistEntry> waitlistEntry() {
        return this instanceof Waitlisted waitlisted ? Optional.of(waitlisted.entry()) : Optional.empty();
    }

    /** The reason, when the outcome is a rejection. */
    default Optional<RejectionReason> rejectionReason() {
        return this instanceof Rejected rejected ? Optional.of(rejected.reason()) : Optional.empty();
    }
}
