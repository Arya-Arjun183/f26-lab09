package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.service.AvailabilityService;
import edu.cmu.cs214.campusreserve.core.service.RoomCatalog;
import edu.cmu.cs214.campusreserve.core.service.RoomSearchCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The room endpoints. Reading the catalog needs no credentials: what rooms exist
 * and when they are free is public.
 *
 * <ul>
 *   <li>{@code GET /rooms} with optional {@code building}, {@code kind}, and
 *       {@code minCapacity}</li>
 *   <li>{@code GET /rooms/{roomId}}</li>
 *   <li>{@code GET /rooms/{roomId}/availability?date=YYYY-MM-DD} with an
 *       optional {@code minutes} to get bookable slots instead of free stretches</li>
 * </ul>
 */
public final class RoomsHandler {

    private final RoomCatalog catalog;
    private final AvailabilityService availability;

    public RoomsHandler(RoomCatalog catalog, AvailabilityService availability) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.availability = Objects.requireNonNull(availability, "availability");
    }

    /** Registers this handler's routes. */
    public void register(Router router) {
        router.get("/rooms", this::list);
        router.get("/rooms/{roomId}/availability", this::availability);
        router.get("/rooms/{roomId}", this::show);
    }

    /** Active rooms matching whatever filters the caller sent. */
    public ApiResponse list(RequestContext request) {
        RoomSearchCriteria criteria = RoomSearchCriteria.any();
        if (request.param("minCapacity").isPresent()) {
            int capacity = request.requireInt("minCapacity");
            if (capacity <= 0) {
                throw ApiException.badRequest("minCapacity must be positive, got " + capacity);
            }
            criteria = criteria.withMinCapacity(capacity);
        }
        if (request.param("kind").isPresent()) {
            criteria = criteria.withKind(parseKind(request.requireParam("kind")));
        }
        if (request.param("building").isPresent()) {
            criteria = criteria.withBuilding(request.requireParam("building"));
        }
        List<Room> rooms = catalog.search(criteria);
        return ApiResponse.ok("rooms", JsonArray.of(rooms, JsonViews::room));
    }

    /** One room, active or not. */
    public ApiResponse show(RequestContext request) {
        String roomId = request.pathParam("roomId");
        Room room = catalog.find(roomId)
            .orElseThrow(() -> ApiException.notFound("No room with id " + roomId));
        return ApiResponse.ok(JsonViews.room(room));
    }

    /** Free time in a room on one UTC date. */
    public ApiResponse availability(RequestContext request) {
        String roomId = request.pathParam("roomId");
        Room room = catalog.find(roomId)
            .orElseThrow(() -> ApiException.notFound("No room with id " + roomId));
        LocalDate date = request.requireDate("date");

        JsonObject body = JsonObject.create()
            .field("roomId", room.id())
            .field("date", date.toString())
            .field("free", JsonArray.of(availability.freeOnDate(roomId, date), JsonViews::interval))
            .field("bookedMinutes", availability.bookedMinutesOnDate(roomId, date))
            .field("utilization", availability.utilizationOnDate(roomId, date), 4);

        if (request.param("minutes").isPresent()) {
            int minutes = request.requireInt("minutes");
            if (minutes <= 0) {
                throw ApiException.badRequest("minutes must be positive, got " + minutes);
            }
            List<TimeInterval> slots = availability.openSlotsOnDate(roomId, date, minutes);
            body.field("slots", JsonArray.of(slots, JsonViews::interval));
        }
        return ApiResponse.ok(body);
    }

    private static RoomKind parseKind(String text) {
        try {
            return RoomKind.fromText(text);
        } catch (IllegalArgumentException failure) {
            throw ApiException.badRequest(failure.getMessage());
        }
    }
}
