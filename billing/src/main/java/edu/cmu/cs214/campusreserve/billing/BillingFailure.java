package edu.cmu.cs214.campusreserve.billing;

/**
 * Why a billing operation was refused. These are the expected refusals, the ones
 * a caller is meant to handle, so they come back inside an
 * {@link InvoiceOutcome} rather than as an exception.
 */
public enum BillingFailure {

    /** No booking with that id. */
    UNKNOWN_BOOKING("No booking with that id"),

    /** The booking names a room the room store does not have. */
    UNKNOWN_ROOM("The booking's room is not in the catalog"),

    /** A bill has already gone out for that booking. */
    ALREADY_INVOICED("That booking has already been invoiced"),

    /** No invoice with that id. */
    UNKNOWN_INVOICE("No invoice with that id"),

    /** That booking has no invoice against it. */
    NO_INVOICE_FOR_BOOKING("No invoice has been issued for that booking"),

    /** The invoice was already paid. */
    ALREADY_PAID("That invoice is already paid"),

    /** The invoice was voided, so it cannot be paid. */
    INVOICE_VOID("That invoice is void"),

    /** Voiding a bill is for cancelled bookings, and this booking is not cancelled. */
    BOOKING_NOT_CANCELLED("That booking is not cancelled");

    private final String message;

    BillingFailure(String message) {
        this.message = message;
    }

    /** A line of text a caller can show without writing its own wording. */
    public String message() {
        return message;
    }
}
