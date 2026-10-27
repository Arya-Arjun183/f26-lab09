package edu.cmu.cs214.campusreserve.cli;

/**
 * One thing the tool can do.
 *
 * <p>A command reads its arguments, works against the services on the context,
 * writes to the console, and returns an exit code: zero when it did what was
 * asked, one when the platform refused. Anything wrong with the arguments
 * themselves goes out as a {@link UsageException} instead, which the entry point
 * turns into exit code 2.
 */
public interface Command {

    /** The word that selects this command, for example {@code list-rooms}. */
    String name();

    /** One line describing the command, shown in the command list. */
    String summary();

    /** The command's shape, for example {@code book --room ID --user ID}. */
    String usage();

    /**
     * Runs the command.
     *
     * @param args the tokens after the command name
     * @param context the wired platform this command works against
     * @param console where output and failures go
     * @return zero when the command did what was asked, one when it was refused
     * @throws UsageException when the arguments cannot be acted on
     */
    int run(Arguments args, CliContext context, Console console);
}
