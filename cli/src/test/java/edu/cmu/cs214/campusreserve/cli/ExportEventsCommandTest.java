package edu.cmu.cs214.campusreserve.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.cli.support.CliHarness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The export is read outside this platform, so these assertions are about the
 * file on disk: one JSON object per line, one line per event, and the promotion
 * carrying the entry it came from.
 */
class ExportEventsCommandTest {

    private final CliHarness cli = new CliHarness();

    @AfterEach
    void tearDown() {
        cli.close();
    }

    @Test
    void writesOneJsonLinePerEvent(@TempDir Path directory) throws IOException {
        cli.seeded();
        assertEquals(0, cli.run("cancel", "--booking", "bk-1", "--user", "u-avery"));

        Path target = directory.resolve("events.jsonl");
        assertEquals(0, cli.run("export-events", "--out", target.toString()));
        assertTrue(cli.out().contains("Wrote 8 events to " + target.toAbsolutePath()));

        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        assertEquals(8, lines.size());
        for (String line : lines) {
            assertTrue(line.startsWith("{\"event\":\""), line);
            assertTrue(line.endsWith("}"), line);
        }
        assertEquals(6, lines.stream().filter(line -> line.contains("\"booking-created\"")).count());
        assertEquals(1, lines.stream().filter(line -> line.contains("\"booking-cancelled\"")).count());

        List<String> promotions = lines.stream()
            .filter(line -> line.contains("\"waitlist-promoted\""))
            .toList();
        assertEquals(1, promotions.size());
        assertTrue(promotions.get(0).contains("\"waitlistEntryId\":\"wl-7\""));
        assertTrue(promotions.get(0).contains("\"roomId\":\"wean-5207\""));
    }

    @Test
    void aChainedRunExportsWhatTheCommandBeforeItDid(@TempDir Path directory) throws IOException {
        // One process, one platform: seeding and exporting have to share a run,
        // because a second invocation would start with empty stores.
        Path target = directory.resolve("events.jsonl");
        assertEquals(0, cli.run("seed", "+", "export-events", "--out", target.toString()));

        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
        assertTrue(lines.size() > 0);
        assertTrue(lines.get(0).startsWith("{\"event\":\""), lines.get(0));
    }

    @Test
    void replacesWhateverWasAtTheTarget(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("nested").resolve("events.jsonl");
        assertEquals(0, cli.run("export-events", "--out", target.toString()));
        assertTrue(cli.out().contains("Wrote 0 events"));
        assertEquals(List.of(), Files.readAllLines(target, StandardCharsets.UTF_8));

        cli.seeded();
        assertEquals(0, cli.run("export-events", "--out", target.toString()));
        assertEquals(6, Files.readAllLines(target, StandardCharsets.UTF_8).size());
    }
}
