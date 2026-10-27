package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingOutcome;
import edu.cmu.cs214.campusreserve.core.domain.BookingRequest;
import edu.cmu.cs214.campusreserve.core.domain.CancellationResult;
import edu.cmu.cs214.campusreserve.core.domain.ModificationResult;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.service.BookingService;
import java.util.List;
import java.util.Objects;

/**
 * The booking endpoints. Every one of them needs a bearer token.
 *
 * <ul>
 *   <li>{@code POST /bookings} with {@code roomId}, {@code start}, and either
 *       {@code end} or {@code minutes}, plus an optional {@code attendees}.
 *       The booking is made for whoever the token belongs to.</li>
 *   <li>{@code GET /bookings/{bookingId}}, owner or administrator</li>
 *   <li>{@code GET /bookings?userId=...}, own bookings unless an administrator</li>
 *   <li>{@code PATCH /bookings/{bookingId}} with {@code start} and either
 *       {@code end} or {@code minutes}, an {@code attendees}, or both, owner
 *       only. Whatever is left out keeps the value the booking already has.</li>
 *   <li>{@code DELETE /bookings/{bookingId}}, owner only</li>
 * </ul>
 *
 * <p>A request for a taken slot is not an error: the caller joins the room's
 * waitlist and gets a 202 saying so.
 */
public final class BookingsHandler {

    private static final int DEFAULT_ATTENDEES = 1;

    private final BookingService bookings;
    private final RequestAuth auth;

    public BookingsHandler(BookingService bookings, RequestAuth auth) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.auth = Objects.requireNonNull(auth, "auth");
    }

    /** Registers this handler's routes. */
    public void register(Router router) {
        router.post("/bookings", this::create);
        router.get("/bookings", this::list);
        router.get("/bookings/{bookingId}", this::show);
        router.add("PATCH", "/bookings/{bookingId}", this::modify);
        router.delete("/bookings/{bookingId}", this::cancel);
    }

    /** Books a room for the caller, or puts the caller on the waitlist. */
    public ApiResponse create(RequestContext request) {
        Account account = auth.require(request);
        int attendees = request.intParam("attendees", DEFAULT_ATTENDEES);
        if (attendees <= 0) {
            throw ApiException.badRequest("attendees must be positive, got " + attendees);
        }
        BookingRequest booking = BookingRequest.of(
            request.requireParam("roomId"), account.userId(), request.requireInterval(), attendees);

        BookingOutcome outcome = bookings.requestBooking(booking);
        if (outcome instanceof BookingOutcome.Confirmed confirmed) {
            return ApiResponse.created(JsonObject.create()
                .field("outcome", "confirmed")
                .field("booking", JsonViews.booking(confirmed.booking())));
        }
        if (outcome instanceof BookingOutcome.Waitlisted waitlisted) {
            return ApiResponse.json(Status.ACCEPTED, JsonObject.create()
                .field("outcome", "waitlisted")
                .field("waitlistEntry", JsonViews.waitlistEntry(waitlisted.entry()))
                .field("position", positionOf(waitlisted.entry().roomId(), waitlisted.entry().id())));
        }
        throw ApiException.of(((BookingOutcome.Rejected) outcome).reason());
    }

    /** One booking, for its owner or an administrator. */
    public ApiResponse show(RequestContext request) {
        String bookingId = request.pathParam("bookingId");
        Booking booking = bookings.findBooking(bookingId)
            .orElseThrow(() -> ApiException.notFound("No booking with id " + bookingId));
        auth.requireActingFor(request, booking.userId());
        return ApiResponse.ok(JsonViews.booking(booking));
    }

    /**
     * The caller's bookings. An administrator may ask for someone else's with
     * {@code userId}; anyone else asking for another user gets a 403.
     */
    public ApiResponse list(RequestContext request) {
        Account account = auth.require(request);
        String userId = request.param("userId").orElse(account.userId());
        if (!userId.equals(account.userId())) {
            auth.requireActingFor(request, userId);
        }
        List<Booking> found = request.param("includeCancelled").isPresent()
            ? bookings.bookingsForUser(userId)
            : bookings.activeBookingsForUser(userId);
        return ApiResponse.ok("bookings", JsonArray.of(found, JsonViews::booking));
    }

    /**
     * Changes an upcoming booking the caller owns, and reports anything promoted
     * into the time the change freed.
     */
    public ApiResponse modify(RequestContext request) {
        String bookingId = request.pathParam("bookingId");
        Account account = auth.require(request);
        Booking booking = bookings.findBooking(bookingId)
            .orElseThrow(() -> ApiException.notFound("No booking with id " + bookingId));

        TimeInterval interval =
            request.param("start").isPresent() ? request.requireInterval() : booking.interval();
        int attendees = request.intParam("attendees", booking.attendees());
        if (attendees <= 0) {
            throw ApiException.badRequest("attendees must be positive, got " + attendees);
        }

        ModificationResult result =
            bookings.modifyBooking(bookingId, account.userId(), interval, attendees);
        if (result instanceof ModificationResult.Rejected rejected) {
            throw ApiException.of(rejected.reason());
        }
        ModificationResult.Modified modified = (ModificationResult.Modified) result;
        return ApiResponse.ok(JsonObject.create()
            .field("booking", JsonViews.booking(modified.booking()))
            .field("promotions", JsonArray.of(modified.promotions(), JsonViews::promotion)));
    }

    /** Cancels a booking and reports anything promoted into the freed time. */
    public ApiResponse cancel(RequestContext request) {
        String bookingId = request.pathParam("bookingId");
        Account account = auth.require(request);

        CancellationResult result = bookings.cancelBooking(bookingId, account.userId());
        if (result instanceof CancellationResult.Rejected rejected) {
            throw ApiException.of(rejected.reason());
        }
        CancellationResult.Cancelled cancelled = (CancellationResult.Cancelled) result;
        return ApiResponse.ok(JsonObject.create()
            .field("booking", JsonViews.booking(cancelled.booking()))
            .field("promotions", JsonArray.of(cancelled.promotions(), JsonViews::promotion)));
    }

    /** Where an entry sits in the room's queue, counting from one. */
    private int positionOf(String roomId, String entryId) {
        List<String> queue = bookings.waitlistForRoom(roomId).stream().map(entry -> entry.id()).toList();
        return queue.indexOf(entryId) + 1;
    }
}
