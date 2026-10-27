package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingStatus;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.ports.BookingStore;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.core.ports.RoomStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns bookings into invoices and tracks what is owed.
 *
 * <p>This is the only writer of invoices in the platform. It holds the rate card
 * in force and prices a booking at the moment the bill goes out, then stores the
 * resulting quote on the invoice. Changing the rate card afterwards moves the
 * price of the next bill and leaves every invoice already issued exactly as it
 * was.
 *
 * <p>Operations that can be refused for an ordinary, expected reason return an
 * {@link InvoiceOutcome} instead of throwing. Exceptions are kept for programmer
 * errors such as a null id.
 */
public final class InvoiceService {

    private static final String INVOICE_ID_PREFIX = "inv";

    private final BookingStore bookings;
    private final RoomStore rooms;
    private final PriceCalculator calculator;
    private final InMemoryInvoiceStore invoices;
    private final IdGenerator ids;
    private final Clock clock;
    private RateCard rateCard;

    public InvoiceService(
        BookingStore bookings,
        RoomStore rooms,
        PriceCalculator calculator,
        InMemoryInvoiceStore invoices,
        IdGenerator ids,
        Clock clock,
        RateCard rateCard) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rateCard = Objects.requireNonNull(rateCard, "rateCard");
    }

    /**
     * A service wired the way the platform runs it: the standard pipeline, the
     * standard rate card, and a fresh invoice store.
     */
    public static InvoiceService standard(BookingStore bookings, RoomStore rooms, IdGenerator ids, Clock clock) {
        return new InvoiceService(
            bookings, rooms, PriceCalculator.standard(), new InMemoryInvoiceStore(),
            ids, clock, RateCard.standard());
    }

    /** The prices new invoices are being written at. */
    public RateCard rateCard() {
        return rateCard;
    }

    /**
     * Puts a new rate card in force. Invoices already issued keep the quote they
     * were issued with, so this only moves the price of bills that have not gone
     * out yet.
     */
    public void useRateCard(RateCard newRateCard) {
        this.rateCard = Objects.requireNonNull(newRateCard, "newRateCard");
    }

    /** The store behind this service, for callers that want to read it directly. */
    public InMemoryInvoiceStore invoices() {
        return invoices;
    }

    /**
     * Prices a booking and issues the bill for it.
     *
     * <p>Refused when the booking is unknown, when its room has left the catalog
     * (there is no rate to price it at), or when a bill has already gone out for
     * that booking. A booking is billed once and once only.
     */
    public InvoiceOutcome issueFor(String bookingId) {
        Optional<Booking> found = bookings.findById(bookingId);
        if (found.isEmpty()) {
            return InvoiceOutcome.failed(BillingFailure.UNKNOWN_BOOKING);
        }
        Booking booking = found.get();
        if (invoices.hasInvoiceFor(booking.id())) {
            return new InvoiceOutcome.Failed(
                BillingFailure.ALREADY_INVOICED, "Booking " + booking.id() + " is already invoiced");
        }
        Optional<Room> room = rooms.findById(booking.roomId());
        if (room.isEmpty()) {
            return new InvoiceOutcome.Failed(
                BillingFailure.UNKNOWN_ROOM, "Room " + booking.roomId() + " is not in the catalog");
        }
        PriceQuote quote = calculator.quote(PriceContext.forBooking(booking, room.get(), rateCard));
        Invoice invoice = Invoice.issued(
            ids.nextId(INVOICE_ID_PREFIX),
            booking.id(),
            booking.userId(),
            booking.roomId(),
            quote,
            now());
        invoices.save(invoice);
        return new InvoiceOutcome.Issued(invoice);
    }

    /**
     * Marks an invoice paid, settled at the current time.
     *
     * <p>Refused when the invoice is unknown, already paid, or void. Paying twice
     * is a mistake worth reporting rather than swallowing.
     */
    public InvoiceOutcome markPaid(String invoiceId) {
        Optional<Invoice> found = invoices.findById(invoiceId);
        if (found.isEmpty()) {
            return InvoiceOutcome.failed(BillingFailure.UNKNOWN_INVOICE);
        }
        Invoice invoice = found.get();
        if (!invoice.status().acceptsPayment()) {
            BillingFailure reason = invoice.status() == InvoiceStatus.PAID
                ? BillingFailure.ALREADY_PAID
                : BillingFailure.INVOICE_VOID;
            return new InvoiceOutcome.Failed(reason, "Invoice " + invoice.id() + ": " + reason.message());
        }
        Invoice paid = invoice.paidAt(now());
        invoices.save(paid);
        return new InvoiceOutcome.Settled(paid);
    }

    /**
     * Voids the bill raised against a cancelled booking, so nothing is owed on
     * time nobody used.
     *
     * <p>Refused when the booking is unknown, when it still holds its room, when
     * no bill was ever raised for it, or when the bill was already paid (money
     * that has changed hands is not something this module can take back). An
     * invoice that is already void comes back unchanged, so a caller can run this
     * twice without special-casing the second run.
     */
    public InvoiceOutcome voidForCancelledBooking(String bookingId) {
        Optional<Booking> found = bookings.findById(bookingId);
        if (found.isEmpty()) {
            return InvoiceOutcome.failed(BillingFailure.UNKNOWN_BOOKING);
        }
        Booking booking = found.get();
        if (booking.status() != BookingStatus.CANCELLED) {
            return new InvoiceOutcome.Failed(
                BillingFailure.BOOKING_NOT_CANCELLED, "Booking " + booking.id() + " still holds its room");
        }
        Optional<Invoice> billed = invoices.findByBooking(booking.id());
        if (billed.isEmpty()) {
            return InvoiceOutcome.failed(BillingFailure.NO_INVOICE_FOR_BOOKING);
        }
        Invoice invoice = billed.get();
        if (invoice.status() == InvoiceStatus.VOID) {
            return new InvoiceOutcome.Voided(invoice);
        }
        if (invoice.status() == InvoiceStatus.PAID) {
            return new InvoiceOutcome.Failed(
                BillingFailure.ALREADY_PAID, "Invoice " + invoice.id() + " was paid and cannot be voided");
        }
        Invoice voided = invoice.voided();
        invoices.save(voided);
        return new InvoiceOutcome.Voided(voided);
    }

    /** The invoice with that id, if there is one. */
    public Optional<Invoice> findById(String invoiceId) {
        return invoices.findById(invoiceId);
    }

    /** The bill raised against that booking, if one was. */
    public Optional<Invoice> findByBooking(String bookingId) {
        return invoices.findByBooking(bookingId);
    }

    /** Every bill for that user, in issue order. */
    public List<Invoice> findByUser(String userId) {
        return invoices.findByUser(userId);
    }

    /**
     * What a user has been billed in total, void invoices left out. Paid ones
     * still count: this is what they were charged, not what they owe.
     */
    public Money totalFor(String userId) {
        Money total = rateCard.zero();
        for (Invoice invoice : invoices.findByUser(userId)) {
            if (invoice.status().isBillable()) {
                total = total.plus(invoice.total());
            }
        }
        return total;
    }

    /** What every user still owes: the sum of the invoices that are neither paid nor void. */
    public Money outstandingTotal() {
        Money total = rateCard.zero();
        for (Invoice invoice : invoices.findAll()) {
            if (invoice.isOutstanding()) {
                total = total.plus(invoice.total());
            }
        }
        return total;
    }

    /** How many invoices sit in that status. */
    public int countByStatus(InvoiceStatus status) {
        return invoices.countByStatus(status);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
