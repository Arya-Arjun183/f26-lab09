package edu.cmu.cs214.campusreserve.cli.commands;

import edu.cmu.cs214.campusreserve.cli.Arguments;
import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Console;
import edu.cmu.cs214.campusreserve.cli.Format;
import edu.cmu.cs214.campusreserve.cli.TableFormatter;
import edu.cmu.cs214.campusreserve.cli.UsageException;
import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The commands that move bookings: book, cancel, list, and read the waitlist. */
public final class BookingCommands {

    private BookingCommands() {
    }

    /**
     * Asks for a room.
     *
     * <p>The three answers the platform can give are reported separately: a
     * confirmation, a place on the waitlist, or a refusal. Only the refusal is a
     * failure, so only the refusal exits non-zero. Landing on the waitlist is a
     * result, not an error.
     */
    public static final class Book extends BaseCommand {

        public Book() {
            super("book", "Ask for a room over an interval.",
                "book --room ID --user ID --start ISO-INSTANT --minutes N [--attendees N]");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("room", "user", "start", "minutes", "attendees").requireNoPositionals();
            String roomId = args.requireString("room");
            String userId = args.requireString("user");
            Instant start = args.requireInstant("start");
            int minutes = args.requireCount("minutes");
            int attendees = args.countOrDefault("attendees", 1);

            TimeInterval interval;
            try {
                interval = TimeInterval.ofMinutes(start, minutes);
            } catch (IllegalArgumentException failure) {
                throw new UsageException("Flag --start must land on a whole minute, and got " + start);
            }
            BookingOutcome outcome = context.bookings()
                .requestBooking(BookingRequest.of(roomId, userId, interval, attendees));

            return switch (outcome) {
                case BookingOutcome.Confirmed confirmed -> {
                    Booking booking = confirmed.booking();
                    console.println("Confirmed " + booking.id() + ": " + booking.roomId() + " for "
                        + booking.userId() + ", " + Format.span(booking.interval()) + ", "
                        + booking.attendees() + " attending");
                    yield 0;
                }
                case BookingOutcome.Waitlisted waitlisted -> {
                    WaitlistEntry entry = waitlisted.entry();
                    console.println("Waitlisted " + entry.id() + " at position " + position(context, entry)
                        + " for " + entry.roomId() + ", " + Format.span(entry.interval()));
                    yield 0;
                }
                case BookingOutcome.Rejected rejected -> {
                    console.error("Refused (" + rejected.reason().code() + "): " + rejected.detail());
                    yield 1;
                }
            };
        }

        /** Where the entry sits in the room's queue, counting from one. */
        private static int position(CliContext context, WaitlistEntry entry) {
            List<WaitlistEntry> queue = context.bookings().waitlistForRoom(entry.roomId());
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).id().equals(entry.id())) {
                    return i + 1;
                }
            }
            return queue.size();
        }
    }

    /**
     * Gives a booking back.
     *
     * <p>Cancelling frees time, and freeing time can pull entries off the room's
     * waitlist. Every promotion the platform made is printed, because the person
     * running this needs to know the room did not simply go empty.
     */
    public static final class Cancel extends BaseCommand {

        public Cancel() {
            super("cancel", "Cancel a booking and report anything promoted.",
                "cancel --booking ID --user ID");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("booking", "user").requireNoPositionals();
            CancellationResult result = context.bookings()
                .cancelBooking(args.requireString("booking"), args.requireString("user"));

            return switch (result) {
                case CancellationResult.Cancelled cancelled -> {
                    Booking booking = cancelled.booking();
                    console.println("Cancelled " + booking.id() + ": " + booking.roomId()
                        + ", " + Format.span(booking.interval()));
                    report(cancelled.promotions(), console);
                    yield 0;
                }
                case CancellationResult.Rejected rejected -> {
                    console.error("Refused (" + rejected.reason().code() + "): " + rejected.detail());
                    yield 1;
                }
            };
        }

        private static void report(List<CancellationResult.Promotion> promotions, Console console) {
            if (promotions.isEmpty()) {
                console.println("Nobody was promoted off the waitlist.");
                return;
            }
            console.println("Promoted " + Format.count(promotions.size(), "entry", "entries") + ":");
            for (CancellationResult.Promotion promotion : promotions) {
                Booking booking = promotion.booking();
                console.println("  " + promotion.entry().id() + " became " + booking.id() + " for "
                    + booking.userId() + ", " + Format.span(booking.interval()));
            }
        }
    }

    /**
     * Lists bookings, narrowed to a room, a user, or both.
     *
     * <p>Cancelled bookings stay in the store as history and are left out unless
     * they are asked for, so the default listing is what currently holds a room.
     */
    public static final class ListBookings extends BaseCommand {

        public ListBookings() {
            super("list-bookings", "List bookings by room, by user, or all of them.",
                "list-bookings [--room ID] [--user ID] [--include-cancelled]");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("room", "user", "include-cancelled").requireNoPositionals();
            Optional<String> roomId = args.optionalString("room");
            Optional<String> userId = args.optionalString("user");
            boolean includeCancelled = args.isPresent("include-cancelled");

            List<Booking> bookings = select(context, roomId, userId).stream()
                .filter(booking -> includeCancelled || booking.holdsRoom())
                .toList();
            if (bookings.isEmpty()) {
                console.println("No booking matches.");
                return 0;
            }
            TableFormatter table =
                new TableFormatter("Booking", "Room", "User", "Start", "End", "People", "Status");
            table.rightAlign(5);
            for (Booking booking : bookings) {
                table.addRow(booking.id(), booking.roomId(), booking.userId(),
                    Format.dateAndTime(booking.interval().start()),
                    Format.clockTime(booking.interval().end()),
                    String.valueOf(booking.attendees()), booking.status().name().toLowerCase());
            }
            console.printTable(table);
            console.println(Format.count(bookings.size(), "booking") + " listed.");
            return 0;
        }

        /**
         * The bookings the flags point at. Naming both a room and a user asks for
         * the bookings that are in both lists.
         */
        private static List<Booking> select(
            CliContext context, Optional<String> roomId, Optional<String> userId) {
            if (roomId.isEmpty()) {
                return userId.map(context.bookings()::bookingsForUser)
                    .orElseGet(() -> context.stores().bookings().findAll());
            }
            List<Booking> inRoom = context.bookings().bookingsForRoom(roomId.get());
            return userId.map(user -> inRoom.stream().filter(booking -> booking.isOwnedBy(user)).toList())
                .orElse(inRoom);
        }
    }

    /**
     * Prints who is waiting for a room, in the order they asked. That order is the
     * order promotion walks when time frees up.
     */
    public static final class Waitlist extends BaseCommand {

        public Waitlist() {
            super("waitlist", "Show who is waiting for a room.", "waitlist --room ID");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("room").requireNoPositionals();
            String roomId = args.requireString("room");
            if (findRoom(context, console, roomId).isEmpty()) {
                return 1;
            }
            List<WaitlistEntry> queue = context.bookings().waitlistForRoom(roomId);
            if (queue.isEmpty()) {
                console.println("Nobody is waiting for " + roomId + ".");
                return 0;
            }
            TableFormatter table =
                new TableFormatter("Position", "Entry", "User", "Start", "End", "People");
            table.rightAlign(0, 5);
            for (int i = 0; i < queue.size(); i++) {
                WaitlistEntry entry = queue.get(i);
                table.addRow(String.valueOf(i + 1), entry.id(), entry.userId(),
                    Format.dateAndTime(entry.interval().start()),
                    Format.clockTime(entry.interval().end()),
                    String.valueOf(entry.attendees()));
            }
            console.printTable(table);
            console.println(Format.count(queue.size(), "entry", "entries") + " waiting.");
            return 0;
        }
    }
}
