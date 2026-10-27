package edu.cmu.cs214.campusreserve.cli.commands;

import edu.cmu.cs214.campusreserve.cli.Arguments;
import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Command;
import edu.cmu.cs214.campusreserve.cli.Console;
import edu.cmu.cs214.campusreserve.cli.Format;
import edu.cmu.cs214.campusreserve.cli.UsageException;
import java.io.UncheckedIOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Writes the event log out as JSON Lines: one event per line, in the order the
 * platform recorded them.
 *
 * <p>The file this produces is read outside the platform, so the export replaces
 * the target rather than appending to it. A reader always sees a whole log and
 * never a log with a previous run stuck on the front. The line shape is decided
 * by the storage module, not here.
 */
public final class ExportEventsCommand implements Command {

    /** Where the log goes when the caller does not say. */
    private static final String DEFAULT_PATH = "events.jsonl";

    @Override
    public String name() {
        return "export-events";
    }

    @Override
    public String summary() {
        return "Write the event log out as JSON Lines.";
    }

    @Override
    public String usage() {
        return "export-events [--out PATH]";
    }

    @Override
    public int run(Arguments args, CliContext context, Console console) {
        args.allow("out").requireNoPositionals();
        Path target = target(args.stringOrDefault("out", DEFAULT_PATH));

        long lines;
        try {
            lines = context.eventLog().writeTo(target);
        } catch (UncheckedIOException failure) {
            console.error("Could not write the event log: " + failure.getMessage());
            return 1;
        }
        console.println("Wrote " + Format.count(lines, "event") + " to " + target.toAbsolutePath());
        return 0;
    }

    private static Path target(String text) {
        try {
            return Path.of(text);
        } catch (InvalidPathException failure) {
            throw new UsageException("Flag --out is not a usable path: " + text);
        }
    }
}
