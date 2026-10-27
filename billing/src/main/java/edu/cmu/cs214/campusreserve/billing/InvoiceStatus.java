package edu.cmu.cs214.campusreserve.billing;

/**
 * Where an invoice stands. An invoice moves from {@code ISSUED} to exactly one
 * of {@code PAID} or {@code VOID}, and then stops. There is no way back.
 */
public enum InvoiceStatus {

    /** Issued and waiting for payment. This is the only status that counts as owed. */
    ISSUED,

    /** Settled. The amount is history now. */
    PAID,

    /** Cancelled without payment. The amount is owed by nobody. */
    VOID;

    /** Is money still owed on an invoice in this status? */
    public boolean isOutstanding() {
        return this == ISSUED;
    }

    /** Does an invoice in this status count towards what a user has been billed? */
    public boolean isBillable() {
        return this != VOID;
    }

    /** Can an invoice in this status be paid? */
    public boolean acceptsPayment() {
        return this == ISSUED;
    }
}
