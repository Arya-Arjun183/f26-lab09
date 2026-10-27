package edu.cmu.cs214.campusreserve.cli.commands;

import edu.cmu.cs214.campusreserve.billing.Invoice;
import edu.cmu.cs214.campusreserve.billing.InvoiceOutcome;
import edu.cmu.cs214.campusreserve.billing.InvoiceStatus;
import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.cli.Arguments;
import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Console;
import edu.cmu.cs214.campusreserve.cli.Format;
import edu.cmu.cs214.campusreserve.cli.TableFormatter;
import java.util.List;
import java.util.Optional;

/** The commands that read billing: one booking's bill, and the totals. */
public final class BillingCommands {

    private BillingCommands() {
    }

    /**
     * Prints the bill for a booking, raising it first if it has not gone out yet.
     *
     * <p>A booking is billed once. Running this twice on the same booking shows
     * the invoice already on file rather than pricing the booking again, so the
     * amount a user was told does not move underneath them.
     */
    public static final class Show extends BaseCommand {

        public Show() {
            super("invoice", "Show a booking's invoice, issuing it if needed.", "invoice --booking ID");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("booking").requireNoPositionals();
            String bookingId = args.requireString("booking");

            Optional<Invoice> onFile = context.invoices().findByBooking(bookingId);
            Invoice invoice;
            if (onFile.isPresent()) {
                invoice = onFile.get();
                console.println("Invoice " + invoice.id() + " was already issued for booking "
                    + bookingId + ".");
            } else {
                InvoiceOutcome outcome = context.invoices().issueFor(bookingId);
                Optional<Invoice> issued = outcome.resultingInvoice();
                if (issued.isEmpty()) {
                    console.error("Refused: " + (outcome instanceof InvoiceOutcome.Failed failed
                        ? failed.detail() : "The invoice could not be issued"));
                    return 1;
                }
                invoice = issued.get();
                console.println("Issued invoice " + invoice.id() + " for booking " + bookingId + ".");
            }

            console.println(Format.field("User", invoice.userId()));
            console.println(Format.field("Room", invoice.roomId()));
            console.println(Format.field("Status", invoice.status().name().toLowerCase()));
            console.println(Format.field("Issued", Format.dateAndTime(invoice.issuedAt())));
            console.blankLine();

            TableFormatter table = new TableFormatter("Code", "Description", "Amount");
            table.rightAlign(2);
            for (LineItem item : invoice.quote().lineItems()) {
                table.addRow(item.code(), item.description(), item.amount().format());
            }
            console.printTable(table);
            console.println(Format.field("Total", invoice.total().format()));
            return 0;
        }
    }

    /**
     * Adds the invoices up: either the whole platform, or one user's.
     *
     * <p>Void invoices are left out of the billed total, because nothing is owed
     * on them and nothing was earned from them. Paid ones stay in: the total is
     * what has been charged, not what is still outstanding.
     */
    public static final class Revenue extends BaseCommand {

        public Revenue() {
            super("revenue", "Add up what has been billed.", "revenue [--user ID]");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("user").requireNoPositionals();
            Optional<String> userId = args.optionalString("user");
            return userId.isPresent()
                ? forUser(userId.get(), context, console)
                : forPlatform(context, console);
        }

        private static int forUser(String userId, CliContext context, Console console) {
            if (context.users().find(userId).isEmpty()) {
                console.error("No user with that id: " + userId);
                return 1;
            }
            List<Invoice> invoices = context.invoices().findByUser(userId);
            if (invoices.isEmpty()) {
                console.println("Nothing has been billed to " + userId + ".");
                return 0;
            }
            TableFormatter table = new TableFormatter("Invoice", "Booking", "Room", "Status", "Amount");
            table.rightAlign(4);
            for (Invoice invoice : invoices) {
                table.addRow(invoice.id(), invoice.bookingId(), invoice.roomId(),
                    invoice.status().name().toLowerCase(), invoice.total().format());
            }
            console.printTable(table);
            console.println(Format.field("Invoices", String.valueOf(invoices.size())));
            console.println(Format.field("Billed", context.invoices().totalFor(userId).format()));
            return 0;
        }

        private static int forPlatform(CliContext context, Console console) {
            List<Invoice> invoices = context.invoices().invoices().findAll();
            Money billed = context.invoices().rateCard().zero();
            for (Invoice invoice : invoices) {
                if (invoice.status().isBillable()) {
                    billed = billed.plus(invoice.total());
                }
            }
            console.println(Format.field("Invoices", String.valueOf(invoices.size())));
            for (InvoiceStatus status : InvoiceStatus.values()) {
                String label = status.name().charAt(0) + status.name().substring(1).toLowerCase();
                console.println(Format.field(label,
                    String.valueOf(context.invoices().countByStatus(status))));
            }
            console.println(Format.field("Billed", billed.format()));
            console.println(Format.field("Owed", context.invoices().outstandingTotal().format()));
            return 0;
        }
    }
}
