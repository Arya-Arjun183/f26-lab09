package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A bill for one booking.
 *
 * <p>The invoice carries the whole quote, not a pointer to the rate card it came
 * from. That snapshot is the point: once a price has been shown to a user, a
 * later change to the rate card must not move it. Reprinting an invoice from
 * last term shows what was charged last term.
 *
 * <p>An invoice is issued, then either paid or voided. It is never revised: the
 * three transitions are the whole life of the record, and each one returns a new
 * invoice rather than changing this one.
 *
 * @param id the invoice id
 * @param bookingId the booking this bills for
 * @param userId who owes it
 * @param roomId which room was booked, copied so a bill reads without a join
 * @param quote the price as it stood when the invoice was issued
 * @param status where the invoice stands
 * @param issuedAt when it was issued
 * @param settledAt when it was paid, and null in every other status
 */
public record Invoice(
    String id,
    String bookingId,
    String userId,
    String roomId,
    PriceQuote quote,
    InvoiceStatus status,
    Instant issuedAt,
    Instant settledAt) {

    public Invoice {
        id = Identifiers.require(id, "invoice id");
        bookingId = Identifiers.require(bookingId, "booking id");
        userId = Identifiers.require(userId, "user id");
        roomId = Identifiers.require(roomId, "room id");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (status == InvoiceStatus.PAID && settledAt == null) {
            throw new IllegalArgumentException("A paid invoice needs a settlement time");
        }
        if (status != InvoiceStatus.PAID && settledAt != null) {
            throw new IllegalArgumentException("Only a paid invoice carries a settlement time");
        }
        if (settledAt != null && settledAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("An invoice cannot settle before it was issued");
        }
    }

    /** Builds a freshly issued invoice. */
    public static Invoice issued(
        String id, String bookingId, String userId, String roomId, PriceQuote quote, Instant issuedAt) {
        return new Invoice(id, bookingId, userId, roomId, quote, InvoiceStatus.ISSUED, issuedAt, null);
    }

    /** A paid copy of this invoice, settled at the given time. */
    public Invoice paidAt(Instant when) {
        Objects.requireNonNull(when, "when");
        return new Invoice(id, bookingId, userId, roomId, quote, InvoiceStatus.PAID, issuedAt, when);
    }

    /** A void copy of this invoice. Nothing is owed on it. */
    public Invoice voided() {
        return new Invoice(id, bookingId, userId, roomId, quote, InvoiceStatus.VOID, issuedAt, null);
    }

    /** What the invoice is for, which is the total of its snapshotted quote. */
    public Money total() {
        return quote.total();
    }

    /** When it was paid, if it was. */
    public Optional<Instant> settledOn() {
        return Optional.ofNullable(settledAt);
    }

    /** Is money still owed on this invoice? */
    public boolean isOutstanding() {
        return status.isOutstanding();
    }
}
