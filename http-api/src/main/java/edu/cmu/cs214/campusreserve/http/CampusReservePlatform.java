package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.AccountDirectory;
import edu.cmu.cs214.campusreserve.accounts.AuthorizationService;
import edu.cmu.cs214.campusreserve.accounts.TokenMinter;
import edu.cmu.cs214.campusreserve.accounts.TokenService;
import edu.cmu.cs214.campusreserve.core.policy.BookingPolicy;
import edu.cmu.cs214.campusreserve.core.ports.IdGenerator;
import edu.cmu.cs214.campusreserve.core.service.AvailabilityService;
import edu.cmu.cs214.campusreserve.core.service.BookingService;
import edu.cmu.cs214.campusreserve.core.service.RoomCatalog;
import edu.cmu.cs214.campusreserve.core.service.UserRegistry;
import edu.cmu.cs214.campusreserve.notifications.InMemoryOutbox;
import edu.cmu.cs214.campusreserve.notifications.NotificationService;
import edu.cmu.cs214.campusreserve.storage.StoreRegistry;
import java.time.Clock;
import java.util.Objects;

/**
 * One running platform: stores, services, accounts, notifications, and the
 * router that puts them on HTTP.
 *
 * <p>This is the only place the modules are wired together, so the dependency
 * edges the repository claims are visible in one file. Notifications are
 * attached as a subscriber to the event publisher, which is what makes them fire
 * and forget: a broken template cannot fail a booking.
 */
public final class CampusReservePlatform implements AutoCloseable {

    private final StoreRegistry stores;
    private final BookingService bookings;
    private final RoomCatalog rooms;
    private final UserRegistry users;
    private final AvailabilityService availability;
    private final AccountDirectory accounts;
    private final TokenService tokens;
    private final AuthorizationService authorization;
    private final NotificationService notifications;
    private final InMemoryOutbox outbox;
    private final Router router;

    private CampusReservePlatform(Clock clock, BookingPolicy policy, IdGenerator ids) {
        Objects.requireNonNull(clock, "clock");
        this.stores = StoreRegistry.inMemory();
        this.bookings = new BookingService(
            stores.bookings(), stores.rooms(), stores.users(), stores.waitlist(),
            stores.publisher(), ids, policy, clock);
        this.rooms = new RoomCatalog(stores.rooms());
        this.users = new UserRegistry(stores.users());
        this.availability = new AvailabilityService(stores.bookings(), policy);

        this.accounts = new AccountDirectory();
        this.tokens = new TokenService(accounts, clock, TokenMinter.random());
        this.authorization = new AuthorizationService(accounts, tokens, clock);

        this.outbox = new InMemoryOutbox();
        this.notifications = new NotificationService(new AccountsUserDirectory(accounts), outbox, clock);
        stores.subscribe(notifications);

        this.router = buildRouter();
    }

    /** A platform on the default booking policy, keyed to a clock. */
    public static CampusReservePlatform create(Clock clock) {
        return new CampusReservePlatform(clock, BookingPolicy.defaults(), IdGenerator.sequential());
    }

    /** A platform on a policy the caller chose. */
    public static CampusReservePlatform create(Clock clock, BookingPolicy policy) {
        return new CampusReservePlatform(clock, policy, IdGenerator.sequential());
    }

    /** A platform on the system clock, for a real deployment. */
    public static CampusReservePlatform createDefault() {
        return new CampusReservePlatform(
            Clock.systemUTC(), BookingPolicy.defaults(), IdGenerator.random());
    }

    /** Starts an HTTP server on this platform. Port 0 picks a free port. */
    public HttpApiServer serveOn(int port) {
        return new HttpApiServer(router, port).start();
    }

    /** The stores behind the platform. */
    public StoreRegistry stores() {
        return stores;
    }

    /** The booking flow. */
    public BookingService bookings() {
        return bookings;
    }

    /** The room catalog. */
    public RoomCatalog rooms() {
        return rooms;
    }

    /** The users the booking domain knows about. */
    public UserRegistry users() {
        return users;
    }

    /** Free and busy time. */
    public AvailabilityService availability() {
        return availability;
    }

    /** Accounts, roles, and addresses. */
    public AccountDirectory accounts() {
        return accounts;
    }

    /** Bearer tokens. */
    public TokenService tokens() {
        return tokens;
    }

    /** The authorization checks the API leans on. */
    public AuthorizationService authorization() {
        return authorization;
    }

    /** Where queued messages land. */
    public InMemoryOutbox outbox() {
        return outbox;
    }

    /** The notification subscriber attached to the event publisher. */
    public NotificationService notifications() {
        return notifications;
    }

    /** The routing table, exposed so the routes can be inspected. */
    public Router router() {
        return router;
    }

    /**
     * Copies every active account into the booking domain's user store, so a
     * person who can log in can also hold a booking.
     */
    public int syncAccountsIntoBookingDomain() {
        return accounts.syncInto(stores.users());
    }

    @Override
    public void close() {
        stores.close();
    }

    private Router buildRouter() {
        RequestAuth auth = new RequestAuth(authorization);
        Router built = new Router();
        built.get("/health", this::health);
        new RoomsHandler(rooms, availability).register(built);
        new BookingsHandler(bookings, auth).register(built);
        new WaitlistHandler(bookings, auth).register(built);
        return built;
    }

    /** A cheap liveness check that also reports how much the platform holds. */
    private ApiResponse health(RequestContext request) {
        return ApiResponse.ok(JsonObject.create()
            .field("status", "ok")
            .field("rooms", stores.rooms().count())
            .field("users", stores.users().count())
            .field("bookings", stores.bookings().count())
            .field("waitlist", stores.waitlist().count())
            .field("routes", router.size()));
    }
}
