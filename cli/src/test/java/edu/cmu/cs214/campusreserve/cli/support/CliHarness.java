package edu.cmu.cs214.campusreserve.cli.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import edu.cmu.cs214.campusreserve.cli.CampusReserveCli;
import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Console;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * One platform, one console over buffers, and a clock that does not move on its
 * own. Each run clears the buffers, so the assertions read the command that just
 * ran rather than everything before it.
 */
public final class CliHarness implements AutoCloseable {

    /** Where the clock starts. The demonstration data lands on the day after. */
    public static final String START = "2026-10-19T08:00:00Z";

    /** The UTC day the seeded bookings sit on. */
    public static final String BOOKING_DAY = "2026-10-20";

    private final TestClock clock = TestClock.at(START);
    private final CliContext context = CliContext.inMemory(clock);
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final Console console = new Console(
        new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));

    /** Runs one command line, chained or not, and returns its exit code. */
    public int run(String... argv) {
        out.reset();
        err.reset();
        return CampusReserveCli.runAll(argv, context, console);
    }

    /** Loads the demonstration data, failing the run if it did not load. */
    public CliHarness seeded() {
        if (run("seed") != 0) {
            throw new IllegalStateException("Seeding failed: " + err());
        }
        return this;
    }

    /** What the last run wrote to the output stream. */
    public String out() {
        return out.toString(UTF_8);
    }

    /** What the last run wrote to the failure stream. */
    public String err() {
        return err.toString(UTF_8);
    }

    /** The clock the platform reads. */
    public TestClock clock() {
        return clock;
    }

    @Override
    public void close() {
        context.close();
    }
}
