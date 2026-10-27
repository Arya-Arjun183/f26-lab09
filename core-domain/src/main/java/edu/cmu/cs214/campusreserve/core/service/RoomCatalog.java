package edu.cmu.cs214.campusreserve.core.service;

import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.ports.RoomStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and edits the room list. Rooms leave service by going inactive, never by
 * disappearing, so bookings already made against them still resolve.
 */
public final class RoomCatalog {

    private final RoomStore rooms;

    public RoomCatalog(RoomStore rooms) {
        this.rooms = Objects.requireNonNull(rooms, "rooms");
    }

    /** Adds a room, or replaces the one already under that id. */
    public void register(Room room) {
        rooms.save(Objects.requireNonNull(room, "room"));
    }

    /** The room with that id, if the catalog has one. */
    public Optional<Room> find(String roomId) {
        return rooms.findById(roomId);
    }

    /** The room with that id, or a failure when there is none. */
    public Room require(String roomId) {
        return rooms.findById(roomId).orElseThrow(
            () -> new IllegalArgumentException(RejectionReason.ROOM_NOT_FOUND.message() + " " + roomId));
    }

    /** Every room the catalog holds. */
    public List<Room> listAll() {
        return rooms.findAll();
    }

    /** Every room currently taking bookings. */
    public List<Room> listActive() {
        return rooms.findActive();
    }

    /** Takes the room out of service. Returns false when there is no such room. */
    public boolean deactivate(String roomId) {
        return setActive(roomId, false);
    }

    /** Puts the room back in service. Returns false when there is no such room. */
    public boolean reactivate(String roomId) {
        return setActive(roomId, true);
    }

    /**
     * Active rooms matching every part of {@code criteria} that is set, ordered by
     * capacity so the smallest room that fits comes first.
     */
    public List<Room> search(RoomSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        return rooms.findActive().stream()
            .filter(criteria::matches)
            .sorted(Comparator.comparingInt(Room::capacity).thenComparing(Room::id))
            .toList();
    }

    /** The smallest active room that seats {@code attendees}, if there is one. */
    public Optional<Room> smallestThatFits(int attendees) {
        return search(RoomSearchCriteria.forParty(attendees)).stream().findFirst();
    }

    /** Every distinct building with at least one active room, in name order. */
    public List<String> buildings() {
        return rooms.findActive().stream()
            .map(Room::building)
            .distinct()
            .sorted()
            .toList();
    }

    /** How many active rooms there are of each kind, in enum order. */
    public List<KindCount> countsByKind() {
        List<Room> active = rooms.findActive();
        return java.util.Arrays.stream(RoomKind.values())
            .map(kind -> new KindCount(kind, (int) active.stream().filter(r -> r.kind() == kind).count()))
            .filter(count -> count.count() > 0)
            .toList();
    }

    /** How many active rooms there are of one kind. */
    public record KindCount(RoomKind kind, int count) {
    }

    private boolean setActive(String roomId, boolean active) {
        Optional<Room> found = rooms.findById(roomId);
        if (found.isEmpty()) {
            return false;
        }
        rooms.save(found.get().withActive(active));
        return true;
    }
}
