package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import edu.cmu.cs214.campusreserve.core.service.BookingService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The waitlist endpoints.
 *
 * <ul>
 *   <li>{@code GET /waitlist?roomId=...} or {@code GET /waitlist?userId=...},
 *       needs a token; a caller may only ask about their own entries unless they
 *       are an administrator</li>
 *   <li>{@code DELETE /waitlist/{entryId}} to give up a place, owner only</li>
 * </ul>
 *
 * <p>Nobody joins the waitlist here. Joining happens by asking for a booking
 * whose slot is taken, which keeps every waitlist write inside the booking
 * service.
 */
public final class WaitlistHandler {

    private final BookingService bookings;
    private final RequestAuth auth;

    public WaitlistHandler(BookingService bookings, RequestAuth auth) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.auth = Objects.requireNonNull(auth, "auth");
    }

    /** Registers this handler's routes. */
    public void register(Router router) {
        router.get("/waitlist", this::list);
        router.delete("/waitlist/{entryId}", this::leave);
    }

    /** A room's queue, or the caller's own entries. */
    public ApiResponse list(RequestContext request) {
        Account account = auth.require(request);
        Optional<String> roomId = request.param("roomId");
        if (roomId.isPresent()) {
            List<WaitlistEntry> queue = bookings.waitlistForRoom(roomId.get());
            return ApiResponse.ok("waitlist", JsonArray.of(queue, JsonViews::waitlistEntry));
        }
        String userId = request.param("userId").orElse(account.userId());
        if (!userId.equals(account.userId())) {
            auth.requireActingFor(request, userId);
        }
        return ApiResponse.ok("waitlist",
            JsonArray.of(bookings.waitlistForUser(userId), JsonViews::waitlistEntry));
    }

    /** Gives up a place in a queue. */
    public ApiResponse leave(RequestContext request) {
        Account account = auth.require(request);
        String entryId = request.pathParam("entryId");
        Optional<RejectionReason> failure = bookings.leaveWaitlist(entryId, account.userId());
        if (failure.isPresent()) {
            throw ApiException.of(failure.get());
        }
        return ApiResponse.ok(JsonObject.create()
            .field("removed", entryId)
            .field("userId", account.userId()));
    }
}
