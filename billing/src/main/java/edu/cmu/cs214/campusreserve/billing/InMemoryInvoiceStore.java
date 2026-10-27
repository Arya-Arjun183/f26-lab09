package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.storage.InMemoryRecordStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Invoices held in memory, keyed by invoice id.
 *
 * <p>Every list query comes back in issue order (oldest first, ties broken by
 * id) so a statement reads top to bottom without the caller sorting. Paid and
 * void invoices stay in the store as history.
 *
 * <p>At most one invoice exists per booking. {@link InvoiceService} is what
 * enforces that, so anything writing here directly has to keep the same rule.
 */
public final class InMemoryInvoiceStore extends InMemoryRecordStore<Invoice> {

    private static final Comparator<Invoice> BY_ISSUE_ORDER =
        Comparator.comparing(Invoice::issuedAt).thenComparing(Invoice::id);

    public InMemoryInvoiceStore() {
        super(Invoice::id);
    }

    /** Inserts the invoice, or replaces the one already under its id. */
    public void save(Invoice invoice) {
        put(invoice);
    }

    /** The invoice with that id, if the store has one. */
    public Optional<Invoice> findById(String invoiceId) {
        return get(invoiceId);
    }

    /** The invoice raised against that booking, if one was ever issued. */
    public Optional<Invoice> findByBooking(String bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        return select(invoice -> invoice.bookingId().equals(bookingId), BY_ISSUE_ORDER)
            .stream()
            .findFirst();
    }

    /** Every invoice billed to that user, in issue order. */
    public List<Invoice> findByUser(String userId) {
        if (userId == null) {
            return List.of();
        }
        return select(invoice -> invoice.userId().equals(userId), BY_ISSUE_ORDER);
    }

    /** Every invoice in that status, in issue order. */
    public List<Invoice> findByStatus(InvoiceStatus status) {
        return select(invoice -> invoice.status() == status, BY_ISSUE_ORDER);
    }

    /** Every invoice in the store, in issue order. */
    public List<Invoice> findAll() {
        return sorted(BY_ISSUE_ORDER);
    }

    /** How many invoices sit in that status. */
    public int countByStatus(InvoiceStatus status) {
        return findByStatus(status).size();
    }

    /** Has a bill already gone out for that booking? */
    public boolean hasInvoiceFor(String bookingId) {
        return findByBooking(bookingId).isPresent();
    }
}
