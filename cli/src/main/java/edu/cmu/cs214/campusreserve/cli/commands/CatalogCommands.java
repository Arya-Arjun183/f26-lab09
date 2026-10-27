package edu.cmu.cs214.campusreserve.cli.commands;

import edu.cmu.cs214.campusreserve.cli.Arguments;
import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Command;
import edu.cmu.cs214.campusreserve.cli.CommandRegistry;
import edu.cmu.cs214.campusreserve.cli.Console;
import edu.cmu.cs214.campusreserve.cli.DemoData;
import edu.cmu.cs214.campusreserve.cli.Format;
import edu.cmu.cs214.campusreserve.cli.TableFormatter;
import edu.cmu.cs214.campusreserve.cli.UsageException;
import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.service.RoomSearchCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The commands that read the catalog, plus the one that fills it. */
public final class CatalogCommands {

    private CatalogCommands() {
    }

    /**
     * Lists the commands, or prints the usage of one of them.
     *
     * <p>The registry arrives as a supplier because the registry holds this
     * command: the supplier is read when help runs, by which point it is built.
     */
    public static final class Help extends BaseCommand {

        private final Supplier<CommandRegistry> registry;

        /**
         * Builds the help command.
         *
         * @param registry gives the registry this command belongs to
         */
        public Help(Supplier<CommandRegistry> registry) {
            super("help", "List the commands, or show what one command takes.", "help [command]");
            this.registry = registry;
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow().requireAtMostPositionals(1);
            CommandRegistry commands = registry.get();
            Optional<String> wanted = args.positional(0);
            if (wanted.isPresent()) {
                Command command = commands.find(wanted.get())
                    .orElseThrow(() -> new UsageException(commands.unknownCommandMessage(wanted.get())));
                console.println("usage: " + command.usage());
                console.println(command.summary());
                return 0;
            }
            console.println("CampusReserve administrative commands.");
            console.blankLine();
            TableFormatter table = new TableFormatter("Command", "Summary");
            commands.commands().forEach(command -> table.addRow(command.name(), command.summary()));
            console.printTable(table);
            console.blankLine();
            console.println("Run help <command> to see what one command takes.");
            console.println("Chain commands with a bare +. They share one in-memory platform for the run.");
            return 0;
        }
    }

    /** Lists the rooms currently taking bookings, narrowed by whatever was asked for. */
    public static final class ListRooms extends BaseCommand {

        public ListRooms() {
            super("list-rooms", "List the rooms taking bookings.",
                "list-rooms [--building NAME] [--kind KIND] [--min-capacity N]");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("building", "kind", "min-capacity").requireNoPositionals();
            RoomSearchCriteria criteria = RoomSearchCriteria.any();
            Optional<String> building = args.optionalString("building");
            if (building.isPresent()) {
                criteria = criteria.withBuilding(building.get());
            }
            Optional<String> kind = args.optionalString("kind");
            if (kind.isPresent()) {
                criteria = criteria.withKind(parseKind(kind.get()));
            }
            if (args.isPresent("min-capacity")) {
                criteria = criteria.withMinCapacity(args.requireCount("min-capacity"));
            }

            List<Room> rooms = context.rooms().search(criteria);
            if (rooms.isEmpty()) {
                console.println("No room matches.");
                return 0;
            }
            TableFormatter table = new TableFormatter("Room", "Name", "Building", "Kind", "Capacity");
            table.rightAlign(4);
            for (Room room : rooms) {
                table.addRow(room.id(), room.name(), room.building(), room.kind().displayName(),
                    String.valueOf(room.capacity()));
            }
            console.printTable(table);
            console.println(Format.count(rooms.size(), "room") + " listed.");
            return 0;
        }

        private static RoomKind parseKind(String text) {
            try {
                return RoomKind.fromText(text);
            } catch (IllegalArgumentException failure) {
                throw new UsageException("Flag --kind takes one of "
                    + "study-room, classroom, lab, auditorium, and got " + text);
            }
        }
    }

    /** Prints one room's details and how much of the calendar it is carrying. */
    public static final class ShowRoom extends BaseCommand {

        public ShowRoom() {
            super("show-room", "Show one room and what it holds.", "show-room --room ID");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("room").requireNoPositionals();
            String roomId = args.requireString("room");
            Optional<Room> found = findRoom(context, console, roomId);
            if (found.isEmpty()) {
                return 1;
            }
            Room room = found.get();
            List<Booking> bookings = context.bookings().bookingsForRoom(roomId);
            long holding = bookings.stream().filter(Booking::holdsRoom).count();

            console.println(Format.field("Room", room.id()));
            console.println(Format.field("Name", room.name()));
            console.println(Format.field("Building", room.building()));
            console.println(Format.field("Kind", room.kind().displayName()));
            console.println(Format.field("Capacity", Format.count(room.capacity(), "seat")));
            console.println(Format.field("Status", room.active() ? "taking bookings" : "out of service"));
            console.println(Format.field("Longest", Format.count(room.kind().maxDurationMinutes(), "minute")));
            console.println(Format.field("Booked",
                Format.count(bookings.size(), "booking") + ", " + holding + " holding the room"));
            console.println(Format.field("Waitlist",
                Format.count(context.bookings().waitlistForRoom(roomId).size(), "entry", "entries")));
            return 0;
        }
    }

    /**
     * Shows what is free in a room on one UTC day, and optionally where a booking
     * of a given length would fit.
     *
     * <p>The free list is the whole of the unbooked time inside opening hours. The
     * slot list is narrower: it only offers starts on a slot boundary, which is
     * what the booking rules will accept.
     */
    public static final class Availability extends BaseCommand {

        public Availability() {
            super("availability", "Show free time in a room on one day.",
                "availability --room ID --date YYYY-MM-DD [--minutes N]");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow("room", "date", "minutes").requireNoPositionals();
            String roomId = args.requireString("room");
            LocalDate date = args.requireDate("date");
            int minutes = args.countOrDefault("minutes", 0);
            if (findRoom(context, console, roomId).isEmpty()) {
                return 1;
            }

            TimeInterval opening = context.availability().openingWindow(date);
            console.println(roomId + " on " + date + ", open " + Format.clockTime(opening.start())
                + " to " + Format.clockTime(opening.end()) + " UTC");

            List<TimeInterval> free = context.availability().freeOnDate(roomId, date);
            if (free.isEmpty()) {
                console.println("Nothing free that day.");
            } else {
                console.printTable(intervalTable(free));
                console.println(Format.count(free.size(), "free stretch", "free stretches") + ".");
            }
            if (minutes > 0) {
                console.blankLine();
                console.println("Slots of " + Format.count(minutes, "minute") + ", starting every "
                    + context.policy().slotGranularityMinutes() + ":");
                List<TimeInterval> slots = context.availability().openSlotsOnDate(roomId, date, minutes);
                if (slots.isEmpty()) {
                    console.println("None that long.");
                } else {
                    console.printTable(intervalTable(slots));
                }
            }
            return 0;
        }

        private static TableFormatter intervalTable(List<TimeInterval> intervals) {
            TableFormatter table = new TableFormatter("Start", "End", "Minutes");
            table.rightAlign(2);
            for (TimeInterval interval : intervals) {
                table.addRow(Format.clockTime(interval.start()), Format.clockTime(interval.end()),
                    String.valueOf(interval.durationMinutes()));
            }
            return table;
        }
    }

    /**
     * Fills an empty platform with the demonstration campus.
     *
     * <p>Refused when rooms are already registered. Loading twice would push the
     * same requests through a platform that already holds them and report a pile
     * of refusals, which reads like a bug in the data rather than a second run.
     */
    public static final class Seed extends BaseCommand {

        public Seed() {
            super("seed", "Load the demonstration rooms, users, and bookings.", "seed");
        }

        @Override
        public int run(Arguments args, CliContext context, Console console) {
            args.allow().requireNoPositionals();
            if (!context.rooms().listAll().isEmpty()) {
                console.error("This platform already holds rooms, so there is nothing to seed.");
                return 1;
            }
            DemoData.Summary summary = DemoData.load(context);
            console.println("Loaded " + Format.count(summary.rooms(), "room")
                + " and " + Format.count(summary.users(), "user") + ".");
            console.println("Placed " + Format.count(summary.confirmed(), "booking") + " and "
                + Format.count(summary.waitlisted(), "waitlist entry", "waitlist entries") + ".");
            if (summary.refused() > 0) {
                console.println(Format.count(summary.refused(), "request") + " was refused.");
            }
            console.println("Bookings sit on " + context.today().plusDays(1) + ", UTC.");
            return 0;
        }
    }
}
