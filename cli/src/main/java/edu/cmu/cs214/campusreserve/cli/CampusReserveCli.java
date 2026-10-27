package edu.cmu.cs214.campusreserve.cli;

import edu.cmu.cs214.campusreserve.cli.commands.BillingCommands;
import edu.cmu.cs214.campusreserve.cli.commands.BookingCommands;
import edu.cmu.cs214.campusreserve.cli.commands.CatalogCommands;
import edu.cmu.cs214.campusreserve.cli.commands.ExportEventsCommand;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The entry point: pick a command, run it, report an exit code.
 *
 * <p>Three codes come out of here and they mean different things. Zero is the
 * command doing what was asked. One is the platform refusing: the room is taken,
 * the booking belongs to somebody else, the file could not be written. Two is
 * the command line itself being wrong, and it comes with the usage line for
 * whatever was being reached for.
 *
 * <p>A run may chain several commands with a bare {@code +} between them. The
 * platform lives in memory for the length of one process, so a command that
 * reads what an earlier one wrote has to run beside it:
 * {@code seed + export-events --out events.jsonl} seeds and then exports the
 * events that seeding produced. Two separate invocations would export nothing,
 * because the second one starts with empty stores.
 *
 * <p>{@link #main(String[])} is the only place in the module that touches the
 * process streams or ends the process. Everything else goes through
 * {@link #runAll(String[], CliContext, Console)}, which returns the code
 * instead.
 */
public final class CampusReserveCli {

    /** The command did what was asked. */
    public static final int EXIT_OK = 0;

    /** The command ran and the platform refused it. */
    public static final int EXIT_FAILURE = 1;

    /** The command line could not be acted on. */
    public static final int EXIT_USAGE = 2;

    /** The token that puts one command after another in a single run. */
    private static final String SEPARATOR = "+";

    private CampusReserveCli() {
    }

    /**
     * Runs a command line against a fresh in-memory platform and ends the process
     * with the code it returned.
     *
     * @param argv one command, or several separated by {@code +}
     */
    public static void main(String[] argv) {
        Console console = new Console(System.out, System.err);
        int code;
        try (CliContext context = CliContext.inMemory()) {
            code = runAll(argv, context, console);
        }
        console.flush();
        System.exit(code);
    }

    /**
     * Runs every command on the line, in order, against one platform.
     *
     * <p>The run stops at the first command that does not return
     * {@link #EXIT_OK}, and that code is what comes back: a chain that cannot get
     * past its second step must not go on to export a half-built platform.
     *
     * @param argv one command, or several separated by {@code +}
     * @param context the platform every command in the chain works against
     * @param console where the commands write
     * @return the code of the first command that did not succeed, or {@link #EXIT_OK}
     */
    public static int runAll(String[] argv, CliContext context, Console console) {
        List<List<String>> segments = new ArrayList<>();
        segments.add(new ArrayList<>());
        for (String token : argv) {
            if (SEPARATOR.equals(token)) {
                segments.add(new ArrayList<>());
            } else {
                segments.get(segments.size() - 1).add(token);
            }
        }
        if (segments.size() > 1 && segments.stream().anyMatch(List::isEmpty)) {
            console.error("A + goes between two commands, and one side of one of them is empty.");
            return EXIT_USAGE;
        }
        int code = EXIT_OK;
        for (List<String> segment : segments) {
            code = run(segment.toArray(new String[0]), context, console);
            if (code != EXIT_OK) {
                return code;
            }
        }
        return code;
    }

    /** Every command the tool ships, in the order help lists them. */
    public static CommandRegistry standardRegistry() {
        AtomicReference<CommandRegistry> holder = new AtomicReference<>();
        CommandRegistry registry = new CommandRegistry(List.of(
            new CatalogCommands.Help(holder::get),
            new CatalogCommands.ListRooms(),
            new CatalogCommands.ShowRoom(),
            new CatalogCommands.Availability(),
            new BookingCommands.Book(),
            new BookingCommands.Cancel(),
            new BookingCommands.ListBookings(),
            new BookingCommands.Waitlist(),
            new BillingCommands.Show(),
            new BillingCommands.Revenue(),
            new CatalogCommands.Seed(),
            new ExportEventsCommand()));
        holder.set(registry);
        return registry;
    }

    /**
     * Dispatches one command line against a platform the caller owns.
     *
     * @param argv the command name followed by its arguments
     * @param context the platform to work against
     * @param console where the command writes
     * @return {@link #EXIT_OK}, {@link #EXIT_FAILURE}, or {@link #EXIT_USAGE}
     */
    public static int run(String[] argv, CliContext context, Console console) {
        CommandRegistry registry = standardRegistry();
        List<String> tokens = List.of(argv);
        if (tokens.isEmpty()) {
            console.error("No command given. Run help to see the commands.");
            return EXIT_USAGE;
        }
        String name = tokens.get(0);
        Optional<Command> found = registry.find(name);
        if (found.isEmpty()) {
            console.error(registry.unknownCommandMessage(name));
            console.error("Run help to see the commands.");
            return EXIT_USAGE;
        }
        Command command = found.get();
        try {
            return command.run(
                Arguments.parse(Arrays.copyOfRange(argv, 1, argv.length)), context, console);
        } catch (UsageException failure) {
            console.error(failure.getMessage());
            console.error("usage: " + command.usage());
            return EXIT_USAGE;
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException failure) {
            console.error(name + " could not finish: " + failure.getMessage());
            return EXIT_FAILURE;
        }
    }
}
