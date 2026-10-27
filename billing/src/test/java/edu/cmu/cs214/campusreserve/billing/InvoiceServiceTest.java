package edu.cmu.cs214.campusreserve.billing;

import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.booking;
import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.interval;
import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.studyRoom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.billing.support.BillingFixtures;
import edu.cmu.cs214.campusreserve.billing.support.TestClock;
import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.storage.InMemoryBookingStore;
import edu.cmu.cs214.campusreserve.storage.InMemoryRoomStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private final InMemoryBookingStore bookings = new InMemoryBookingStore();
    private final InMemoryRoomStore rooms = new InMemoryRoomStore();
    private final TestClock clock = TestClock.at(BillingFixtures.NOW);
    private final Room room = studyRoom("r1");

    private InvoiceService service;

    @BeforeEach
    void wire() {
        rooms.save(room);
        service = InvoiceService.standard(bookings, rooms, IdGenerator.sequential(), clock);
    }

    @Test
    void issuesAnInvoiceForTheBookedTime() {
        save("bk-1", "u1", "09:00", "10:30");

        InvoiceOutcome outcome = service.issueFor("bk-1");

        Invoice invoice = assertInstanceOf(InvoiceOutcome.Issued.class, outcome).invoice();
        assertEquals("inv-1", invoice.id());
        assertEquals("bk-1", invoice.bookingId());
        assertEquals("u1", invoice.userId());
        assertEquals("r1", invoice.roomId());
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
        assertEquals(Instant.parse(BillingFixtures.NOW), invoice.issuedAt());
        assertTrue(invoice.settledOn().isEmpty());
        assertTrue(invoice.isOutstanding());
        assertEquals(Money.usd(750), invoice.total());
        assertEquals(invoice, service.findByBooking("bk-1").orElseThrow());
        assertTrue(service.invoices().hasInvoiceFor("bk-1"));
    }

    @Test
    void refusesToInvoiceWhatItCannotBill() {
        issue("bk-1", "u1", "09:00", "10:30");
        bookings.save(Booking.confirmed(
            "bk-gone", "r-gone", "u1", interval("09:00", "10:00"), 4, Instant.parse(BillingFixtures.NOW)));

        assertEquals(BillingFailure.UNKNOWN_BOOKING, failure(service.issueFor("bk-missing")));
        assertEquals(BillingFailure.UNKNOWN_ROOM, failure(service.issueFor("bk-gone")));
        assertEquals(BillingFailure.ALREADY_INVOICED, failure(service.issueFor("bk-1")));
        assertEquals(1, service.invoices().count());
    }

    @Test
    void settlesOneInvoiceAndVoidsTheBillForACancelledBooking() {
        String paidId = issue("bk-1", "u1", "09:00", "10:30");
        clock.advanceMinutes(120);

        Invoice paid = assertInstanceOf(InvoiceOutcome.Settled.class, service.markPaid(paidId)).invoice();
        assertEquals(InvoiceStatus.PAID, paid.status());
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), paid.settledOn().orElseThrow());
        assertEquals(paid, service.findById(paidId).orElseThrow());
        assertEquals(1, service.invoices().count());
        assertEquals(1, service.countByStatus(InvoiceStatus.PAID));

        issue("bk-2", "u2", "11:00", "12:30");
        cancel("bk-2");
        Invoice voided = assertInstanceOf(
            InvoiceOutcome.Voided.class, service.voidForCancelledBooking("bk-2")).invoice();
        assertEquals(InvoiceStatus.VOID, voided.status());
        assertEquals(Money.usd(750), voided.total());
        assertTrue(service.voidForCancelledBooking("bk-2").isSuccess());
        assertEquals(1, service.countByStatus(InvoiceStatus.VOID));
    }

    @Test
    void refusesEveryPaymentAndVoidThatDoesNotApply() {
        String paid = issue("bk-1", "u1", "09:00", "10:30");
        service.markPaid(paid);
        String voided = issue("bk-2", "u2", "11:00", "12:00");
        cancel("bk-2");
        service.voidForCancelledBooking("bk-2");
        issue("bk-3", "u1", "13:00", "14:00");
        save("bk-4", "u2", "15:00", "16:00");
        cancel("bk-4");

        assertEquals(BillingFailure.ALREADY_PAID, failure(service.markPaid(paid)));
        assertEquals(BillingFailure.INVOICE_VOID, failure(service.markPaid(voided)));
        assertEquals(BillingFailure.UNKNOWN_INVOICE, failure(service.markPaid("inv-99")));
        assertEquals(BillingFailure.BOOKING_NOT_CANCELLED, failure(service.voidForCancelledBooking("bk-3")));
        assertEquals(BillingFailure.NO_INVOICE_FOR_BOOKING, failure(service.voidForCancelledBooking("bk-4")));
        assertEquals(BillingFailure.UNKNOWN_BOOKING, failure(service.voidForCancelledBooking("bk-missing")));

        cancel("bk-1");
        assertEquals(BillingFailure.ALREADY_PAID, failure(service.voidForCancelledBooking("bk-1")));
        assertEquals(InvoiceStatus.PAID, service.findById(paid).orElseThrow().status());
    }

    @Test
    void anIssuedPriceDoesNotMoveWhenTheRateCardDoes() {
        save("bk-1", "u1", "09:00", "10:30");
        save("bk-2", "u1", "11:00", "12:30");
        Invoice first = service.issueFor("bk-1").orElseThrow();
        assertEquals(Money.usd(750), first.total());

        service.useRateCard(RateCard.standard().withHourlyRate(RoomKind.STUDY_ROOM, Money.usd(2_000)));

        assertEquals(Money.usd(750), service.findById(first.id()).orElseThrow().total());
        assertEquals("90 min in Study r1 (Study room) at USD 5.00 per hour",
            service.findById(first.id()).orElseThrow().quote().lineItem("BASE").orElseThrow().description());
        assertEquals(Money.usd(3_000), service.issueFor("bk-2").orElseThrow().total());
    }

    @Test
    void reportsWhatEachUserWasBilledAndWhatIsStillOwed() {
        issue("bk-1", "u1", "09:00", "10:30");
        clock.advanceMinutes(1);
        String secondId = issue("bk-2", "u1", "11:00", "12:30");
        clock.advanceMinutes(1);
        issue("bk-3", "u2", "13:00", "14:00");

        assertEquals(List.of("inv-1", "inv-2", "inv-3"), ids(service.invoices().findAll()));
        assertEquals(List.of("inv-1", "inv-2"), ids(service.findByUser("u1")));
        assertEquals(List.of("inv-3"), ids(service.findByUser("u2")));
        assertEquals(Money.usd(1_500), service.totalFor("u1"));
        assertEquals(Money.usd(600), service.totalFor("u2"));
        assertEquals(Money.usd(2_100), service.outstandingTotal());

        service.markPaid(secondId);
        assertEquals(Money.usd(1_500), service.totalFor("u1"));
        assertEquals(Money.usd(1_350), service.outstandingTotal());

        cancel("bk-1");
        service.voidForCancelledBooking("bk-1");
        assertEquals(Money.usd(750), service.totalFor("u1"));
        assertEquals(Money.usd(600), service.outstandingTotal());
        assertEquals(Money.zero("USD"), service.totalFor("u9"));
        assertEquals(1, service.invoices().findByStatus(InvoiceStatus.ISSUED).size());
    }

    private void save(String bookingId, String userId, String from, String to) {
        bookings.save(booking(bookingId, room, userId, interval(from, to), 4));
    }

    private String issue(String bookingId, String userId, String from, String to) {
        save(bookingId, userId, from, to);
        return service.issueFor(bookingId).orElseThrow().id();
    }

    private void cancel(String bookingId) {
        bookings.save(bookings.findById(bookingId).orElseThrow().cancelled());
    }

    private static BillingFailure failure(InvoiceOutcome outcome) {
        return outcome.failure().orElseThrow();
    }

    private static List<String> ids(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::id).toList();
    }
}
