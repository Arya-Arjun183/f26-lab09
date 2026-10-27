package edu.cmu.cs214.campusreserve.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.cli.support.CliHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Every command, run end to end against a seeded platform on a clock that does not move. */
class CommandsTest {

    private final CliHarness cli = new CliHarness();

    @AfterEach
    void tearDown() {
        cli.close();
    }

    @Nested
    class Catalog {

        @Test
        void helpListsEveryCommandAndThenOne() {
            assertEquals(0, cli.run("help"));
            assertTrue(cli.out().contains("export-events"));
            assertTrue(cli.out().contains("Ask for a room over an interval."));

            assertEquals(0, cli.run("help", "book"));
            assertTrue(cli.out().startsWith("usage: book --room ID --user ID"));

            assertEquals(2, cli.run("help", "boook"));
            assertTrue(cli.err().contains("Did you mean book?"));
        }

        @Test
        void seedLoadsTheCampusOnce() {
            assertEquals(0, cli.run("seed"));
            assertTrue(cli.out().contains("Loaded 8 rooms and 5 users."));
            assertTrue(cli.out().contains("Placed 6 bookings and 1 waitlist entry."));
            assertTrue(cli.out().contains("Bookings sit on " + CliHarness.BOOKING_DAY));

            assertEquals(1, cli.run("seed"));
            assertTrue(cli.err().contains("already holds rooms"));
        }

        @Test
        void listRoomsNarrowsByBuildingKindAndCapacity() {
            cli.seeded();
            assertEquals(0, cli.run("list-rooms"));
            assertTrue(cli.out().contains("8 rooms listed."));

            assertEquals(0, cli.run("list-rooms", "--building", "Gates Center"));
            assertTrue(cli.out().contains("3 rooms listed."));
            assertFalse(cli.out().contains("wean-5207"));

            assertEquals(0, cli.run("list-rooms", "--kind", "auditorium", "--min-capacity", "100"));
            assertTrue(cli.out().contains("baker-a51"));
            assertTrue(cli.out().contains("1 room listed."));

            assertEquals(0, cli.run("list-rooms", "--kind", "lab", "--min-capacity", "1000"));
            assertEquals("No room matches." + System.lineSeparator(), cli.out());

            assertEquals(2, cli.run("list-rooms", "--kind", "broom-cupboard"));
            assertTrue(cli.err().contains("--kind takes one of"));
        }

        @Test
        void showRoomCountsWhatTheRoomIsCarrying() {
            cli.seeded();
            assertEquals(0, cli.run("show-room", "--room", "wean-5207"));
            assertTrue(cli.out().contains("Wean 5207"));
            assertTrue(cli.out().contains("8 seats"));
            assertTrue(cli.out().contains("2 bookings, 2 holding the room"));

            assertEquals(1, cli.run("show-room", "--room", "nowhere-1"));
            assertTrue(cli.err().contains("No room with that id: nowhere-1"));
        }

        @Test
        void availabilityShowsFreeTimeAndSlotsThatFit() {
            cli.seeded();
            assertEquals(0, cli.run("availability", "--room", "wean-5207", "--date", CliHarness.BOOKING_DAY));
            assertTrue(cli.out().contains("open 06:00 to 22:00 UTC"));
            assertTrue(cli.out().contains("06:00  09:00"));
            assertTrue(cli.out().contains("3 free stretches."));

            assertEquals(0, cli.run("availability", "--room", "wean-5207",
                "--date", CliHarness.BOOKING_DAY, "--minutes", "60"));
            assertTrue(cli.out().contains("Slots of 60 minutes, starting every 15:"));
            assertTrue(cli.out().contains("08:00  09:00"));

            assertEquals(1, cli.run("availability", "--room", "nowhere-1", "--date", CliHarness.BOOKING_DAY));
        }
    }

    @Nested
    class Bookings {

        @Test
        void bookConfirmsAFreeSlot() {
            cli.seeded();
            assertEquals(0, cli.run("book", "--room", "wean-5409", "--user", "u-devi",
                "--start", "2026-10-20T09:00:00Z", "--minutes", "60", "--attendees", "3"));
            assertTrue(cli.out().contains("wean-5409 for u-devi"));
            assertTrue(cli.out().contains("2026-10-20 09:00 to 10:00"));
            assertTrue(cli.out().contains("3 attending"));
        }

        @Test
        void bookJoinsTheWaitlistWhenTheSlotIsTaken() {
            cli.seeded();
            assertEquals(0, cli.run("book", "--room", "wean-5207", "--user", "u-devi",
                "--start", "2026-10-20T09:00:00Z", "--minutes", "60"));
            assertTrue(cli.out().contains("Waitlisted"));
            assertTrue(cli.out().contains("at position 2"));
        }

        @Test
        void bookExitsNonZeroWhenThePlatformRefuses() {
            cli.seeded();
            assertEquals(1, cli.run("book", "--room", "hunt-a15", "--user", "u-devi",
                "--start", "2026-10-20T16:00:00Z", "--minutes", "60", "--attendees", "50"));
            assertTrue(cli.err().contains("attendees-exceed-capacity"));
            assertEquals("", cli.out());
        }

        @Test
        void bookRefusesAStartOffTheMinute() {
            cli.seeded();
            assertEquals(2, cli.run("book", "--room", "wean-5409", "--user", "u-devi",
                "--start", "2026-10-20T09:00:30Z", "--minutes", "60"));
            assertTrue(cli.err().contains("--start must land on a whole minute"));
        }

        @Test
        void cancelFreesTimeAndPromotesTheWaiter() {
            cli.seeded();
            assertEquals(0, cli.run("cancel", "--booking", "bk-1", "--user", "u-avery"));
            assertTrue(cli.out().contains("Cancelled bk-1: wean-5207"));
            assertTrue(cli.out().contains("Promoted 1 entry:"));
            assertTrue(cli.out().contains("wl-7 became"));
            assertTrue(cli.out().contains("for u-casey"));

            assertEquals(0, cli.run("waitlist", "--room", "wean-5207"));
            assertTrue(cli.out().contains("Nobody is waiting for wean-5207."));
        }

        @Test
        void cancelIsRefusedForTheWrongUserAndAfterTheStart() {
            cli.seeded();
            assertEquals(1, cli.run("cancel", "--booking", "bk-1", "--user", "u-blair"));
            assertTrue(cli.err().contains("not-booking-owner"));

            cli.clock().advanceMinutes(1_500);
            assertEquals(1, cli.run("cancel", "--booking", "bk-1", "--user", "u-avery"));
            assertTrue(cli.err().contains("booking-already-started"));
        }

        @Test
        void waitlistShowsTheQueueInArrivalOrder() {
            cli.seeded();
            assertEquals(0, cli.run("waitlist", "--room", "wean-5207"));
            assertTrue(cli.out().contains("Position"));
            assertTrue(cli.out().contains("wl-7"));
            assertTrue(cli.out().contains("u-casey"));
            assertTrue(cli.out().contains("1 entry waiting."));

            assertEquals(0, cli.run("waitlist", "--room", "hunt-a15"));
            assertTrue(cli.out().contains("Nobody is waiting"));
            assertEquals(1, cli.run("waitlist", "--room", "nowhere-1"));
        }

        @Test
        void listBookingsNarrowsAndHidesCancelledUntilAsked() {
            cli.seeded();
            assertEquals(0, cli.run("list-bookings"));
            assertTrue(cli.out().contains("6 bookings listed."));

            assertEquals(0, cli.run("list-bookings", "--user", "u-avery"));
            assertTrue(cli.out().contains("2 bookings listed."));

            assertEquals(0, cli.run("list-bookings", "--room", "wean-5207", "--user", "u-avery"));
            assertTrue(cli.out().contains("1 booking listed."));

            cli.run("cancel", "--booking", "bk-6", "--user", "u-avery");
            assertEquals(0, cli.run("list-bookings", "--user", "u-avery"));
            assertFalse(cli.out().contains("bk-6"));

            assertEquals(0, cli.run("list-bookings", "--user", "u-avery", "--include-cancelled"));
            assertTrue(cli.out().contains("bk-6"));
            assertTrue(cli.out().contains("cancelled"));

            assertEquals(0, cli.run("list-bookings", "--user", "u-nobody"));
            assertEquals("No booking matches." + System.lineSeparator(), cli.out());
        }
    }

    @Nested
    class Billing {

        @Test
        void invoiceIssuesOnceAndThenShowsWhatIsOnFile() {
            cli.seeded();
            assertEquals(0, cli.run("invoice", "--booking", "bk-1"));
            assertTrue(cli.out().contains("Issued invoice inv-1 for booking bk-1."));
            assertTrue(cli.out().contains("BASE  60 min in Wean 5207 (Study room)"));
            assertTrue(cli.out().contains("Total      USD 5.00"));

            assertEquals(0, cli.run("invoice", "--booking", "bk-1"));
            assertTrue(cli.out().contains("Invoice inv-1 was already issued for booking bk-1."));

            assertEquals(1, cli.run("invoice", "--booking", "bk-99"));
            assertTrue(cli.err().contains("No booking with that id"));
        }

        @Test
        void revenueAddsUpOneUserAndThenThePlatform() {
            cli.seeded();
            assertEquals(0, cli.run("revenue", "--user", "u-avery"));
            assertTrue(cli.out().contains("Nothing has been billed to u-avery."));

            cli.run("invoice", "--booking", "bk-1");
            assertEquals(0, cli.run("revenue", "--user", "u-avery"));
            assertTrue(cli.out().contains("inv-1"));
            assertTrue(cli.out().contains("Billed     USD 5.00"));

            assertEquals(0, cli.run("revenue"));
            assertTrue(cli.out().contains("Invoices   1"));
            assertTrue(cli.out().contains("Issued     1"));
            assertTrue(cli.out().contains("Void       0"));
            assertTrue(cli.out().contains("Owed       USD 5.00"));

            assertEquals(1, cli.run("revenue", "--user", "u-nobody"));
            assertTrue(cli.err().contains("No user with that id: u-nobody"));
        }
    }

    @Nested
    class Dispatch {

        @Test
        void aBareCommandLineIsAUsageError() {
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run());
            assertTrue(cli.err().contains("No command given."));
        }

        @Test
        void anUnknownCommandSuggestsTheNearestOne() {
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run("list-room"));
            assertTrue(cli.err().contains("Did you mean list-rooms?"));
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run("xyzzy"));
            assertEquals("Unknown command: xyzzy" + System.lineSeparator()
                + "Run help to see the commands." + System.lineSeparator(), cli.err());
        }

        @Test
        void aMissingFlagPrintsTheUsageOfTheCommandItWasFor() {
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run("book", "--room", "wean-5207"));
            assertTrue(cli.err().contains("Flag --user is required"));
            assertTrue(cli.err().contains("usage: book --room ID --user ID"));
        }

        @Test
        void anUnknownFlagStopsTheCommandRatherThanBeingIgnored() {
            cli.seeded();
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run("list-rooms", "--biulding", "Wean Hall"));
            assertTrue(cli.err().contains("Unknown flag --biulding"));
            assertTrue(cli.err().contains("This command takes: --building, --kind, --min-capacity"));
        }

        @Test
        void aChainStopsAtTheFirstFailureAndRefusesADanglingPlus() {
            assertEquals(CampusReserveCli.EXIT_USAGE, cli.run("seed", "+"));
            assertTrue(cli.err().contains("A + goes between two commands"));

            assertEquals(CampusReserveCli.EXIT_FAILURE,
                cli.run("seed", "+", "show-room", "--room", "nowhere-1", "+", "list-rooms"));
            assertFalse(cli.out().contains("rooms listed."));
        }

        @Test
        void aSuccessExitsZeroAndARefusalExitsOne() {
            cli.seeded();
            assertEquals(CampusReserveCli.EXIT_OK, cli.run("list-rooms"));
            assertEquals(CampusReserveCli.EXIT_FAILURE, cli.run("show-room", "--room", "nowhere-1"));
        }
    }

    @Nested
    class Listings {

        @Test
        void tablesPadColumnsAndRightAlignNumericOnes() {
            TableFormatter table = new TableFormatter("Room", "Capacity");
            table.rightAlign(1);
            table.addRow("wean-5207", "8");
            table.addRow("baker-a51", "250");
            assertEquals(java.util.List.of(
                "Room       Capacity",
                "---------  --------",
                "wean-5207         8",
                "baker-a51       250"), table.render());
        }

        @Test
        void theRegistryListsCommandsInOrderAndFindsThemByName() {
            CommandRegistry registry = CampusReserveCli.standardRegistry();
            assertEquals(12, registry.size());
            assertEquals("help", registry.commands().get(0).name());
            assertEquals("export-events", registry.commands().get(11).name());
            assertEquals("book", registry.find("book").orElseThrow().name());
            assertFalse(registry.find("Book").isPresent());
            assertEquals("waitlist", registry.closestName("watilist").orElseThrow());
            assertFalse(registry.closestName("xyzzy").isPresent());
        }
    }
}
