package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.accounts.Role;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import java.time.Clock;
import java.time.Duration;

/**
 * Starts the API on a local port with a small catalog loaded, and prints a
 * bearer token so the endpoints can be tried with curl.
 *
 * <p>Everything is in memory, so stopping the process throws the data away.
 */
public final class CampusReserveServer {

    private static final int DEFAULT_PORT = 8080;

    private CampusReserveServer() {
    }

    /** Starts the server. The first argument is the port, and defaults to 8080. */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        CampusReservePlatform platform = CampusReservePlatform.createDefault();
        seed(platform);

        HttpApiServer server = platform.serveOn(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            platform.close();
        }));

        String member = platform.tokens().issue("u-1", Duration.ofHours(8)).value();
        String admin = platform.tokens().issue("u-2", Duration.ofHours(8)).value();

        System.out.println("CampusReserve is listening on " + server.baseUrl());
        System.out.println("Try:  curl " + server.baseUrl() + "/rooms");
        System.out.println();
        System.out.println("Two accounts, so ownership and administrator checks can both be tried:");
        System.out.println("  u-1 (member) " + member);
        System.out.println("  u-2 (admin)  " + admin);
        System.out.println();
        System.out.println("  curl -H \"Authorization: Bearer " + member + "\" "
            + server.baseUrl() + "/bookings");
    }

    private static void seed(CampusReservePlatform platform) {
        platform.accounts().register(new Account(
            "u-1", "Ada", "ada@andrew.cmu.edu", Role.MEMBER, true, Clock.systemUTC().instant()));
        platform.accounts().register(new Account(
            "u-2", "Cass", "cass@andrew.cmu.edu", Role.ADMIN, true, Clock.systemUTC().instant()));
        platform.syncAccountsIntoBookingDomain();

        platform.rooms().register(Room.active("r-1", "Study 205", "Sorrells", 8, RoomKind.STUDY_ROOM));
        platform.rooms().register(Room.active("r-2", "Study 206", "Sorrells", 4, RoomKind.STUDY_ROOM));
        platform.rooms().register(Room.active("r-3", "Lecture 1", "Wean", 60, RoomKind.CLASSROOM));
        platform.rooms().register(Room.active("r-4", "Bench Lab", "Hamerschlag", 12, RoomKind.LAB));
    }
}
