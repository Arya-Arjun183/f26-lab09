package edu.cmu.cs214.campusreserve.billing;

import java.util.Objects;
import java.util.Optional;

/**
 * What came of a billing operation: a new invoice, a settled one, a voided one,
 * or a refusal that left everything as it was.
 *
 * <p>The type is sealed, so a caller can switch over it and the compiler will
 * point out a branch that was never written.
 */
public sealed interface InvoiceOutcome {

    /** A bill went out for a booking. */
    record Issued(Invoice invoice) implements InvoiceOutcome {
        public Issued {
            Objects.requireNonNull(invoice, "invoice");
        }
    }

    /** An invoice was paid. */
    record Settled(Invoice invoice) implements InvoiceOutcome {
        public Settled {
            Objects.requireNonNull(invoice, "invoice");
        }
    }

    /** An invoice was voided. Nothing is owed on it now. */
    record Voided(Invoice invoice) implements InvoiceOutcome {
        public Voided {
            Objects.requireNonNull(invoice, "invoice");
        }
    }

    /** The operation was refused. No invoice changed. */
    record Failed(BillingFailure reason, String detail) implements InvoiceOutcome {
        public Failed {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.message() : detail;
        }
    }

    /** Builds a refusal carrying the reason's own wording. */
    static InvoiceOutcome failed(BillingFailure reason) {
        return new Failed(reason, reason.message());
    }

    /** Did the operation go through? */
    default boolean isSuccess() {
        return !(this instanceof Failed);
    }

    /** The invoice the operation produced, empty when it was refused. */
    default Optional<Invoice> resultingInvoice() {
        return switch (this) {
            case Issued issued -> Optional.of(issued.invoice());
            case Settled settled -> Optional.of(settled.invoice());
            case Voided voided -> Optional.of(voided.invoice());
            case Failed ignored -> Optional.empty();
        };
    }

    /** Why it was refused, empty when it went through. */
    default Optional<BillingFailure> failure() {
        return this instanceof Failed refused ? Optional.of(refused.reason()) : Optional.empty();
    }

    /**
     * The invoice, or an exception when the operation was refused. For callers
     * that have already checked, and for setup code that treats a refusal here as
     * a bug rather than a case to handle.
     */
    default Invoice orElseThrow() {
        return resultingInvoice().orElseThrow(() -> new IllegalStateException(
            this instanceof Failed refused ? refused.detail() : "No invoice"));
    }
}
