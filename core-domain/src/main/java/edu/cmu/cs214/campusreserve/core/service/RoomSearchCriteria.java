package edu.cmu.cs214.campusreserve.core.service;

import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import java.util.Optional;

/**
 * What a caller is looking for in the room catalog. Every field is optional; an
 * unset field places no constraint.
 */
public record RoomSearchCriteria(Optional<Integer> minCapacity, Optional<RoomKind> kind, Optional<String> building) {

    public RoomSearchCriteria {
        if (minCapacity.isPresent() && minCapacity.get() <= 0) {
            throw new IllegalArgumentException("Minimum capacity must be positive: " + minCapacity.get());
        }
    }

    /** No constraints at all. */
    public static RoomSearchCriteria any() {
        return new RoomSearchCriteria(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Rooms that seat at least this many people. */
    public static RoomSearchCriteria forParty(int attendees) {
        return any().withMinCapacity(attendees);
    }

    /** A copy that also requires the given capacity. */
    public RoomSearchCriteria withMinCapacity(int capacity) {
        return new RoomSearchCriteria(Optional.of(capacity), kind, building);
    }

    /** A copy that also requires the given kind. */
    public RoomSearchCriteria withKind(RoomKind requiredKind) {
        return new RoomSearchCriteria(minCapacity, Optional.of(requiredKind), building);
    }

    /** A copy that also requires the given building. */
    public RoomSearchCriteria withBuilding(String requiredBuilding) {
        return new RoomSearchCriteria(minCapacity, kind, Optional.of(requiredBuilding));
    }

    /** Does the room satisfy every constraint that is set? */
    public boolean matches(Room room) {
        if (minCapacity.isPresent() && room.capacity() < minCapacity.get()) {
            return false;
        }
        if (kind.isPresent() && room.kind() != kind.get()) {
            return false;
        }
        return building.isEmpty() || room.building().equalsIgnoreCase(building.get());
    }
}
