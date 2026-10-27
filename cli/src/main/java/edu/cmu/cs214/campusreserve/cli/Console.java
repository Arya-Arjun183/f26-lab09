package edu.cmu.cs214.campusreserve.cli;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Where a command writes. Ordinary output goes to one stream and failures go to
 * another, and both are handed in from outside.
 *
 * <p>Nothing below {@code main} reaches for the process streams itself, so a
 * test can point a console at a buffer and read back exactly what a command
 * printed.
 */
public final class Console {

    private final PrintStream out;
    private final PrintStream err;

    /**
     * Builds a console over two streams.
     *
     * @param out where results go
     * @param err where failures and usage complaints go
     */
    public Console(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /** Writes one line of output. */
    public void println(String line) {
        out.println(line);
    }

    /** Writes an empty line, used to separate sections of a listing. */
    public void blankLine() {
        out.println();
    }

    /** Writes a rendered table: the header, the rule under it, then the rows. */
    public void printTable(TableFormatter table) {
        table.render().forEach(out::println);
    }

    /** Writes one line to the failure stream. */
    public void error(String line) {
        err.println(line);
    }

    /** Pushes both streams out, which the entry point does before it exits. */
    public void flush() {
        out.flush();
        err.flush();
    }
}
