package edu.cmu.cs214.campusreserve.core.domain;

import java.util.Objects;

/**
 * A bookable room. Rooms are never deleted once they have bookings against
 * them; a room that leaves service is marked inactive instead.
 */
public record Room(
    String id,
    String name,
    String building,
    int capacity,
    RoomKind kind,
    boolean active) {

    public Room {
        id = Identifiers.require(id, "room id");
        name = Identifiers.requireText(name, "room name");
        building = Identifiers.requireText(building, "building");
        Objects.requireNonNull(kind, "kind");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
    }

    /** Builds an active room. */
    public static Room active(String id, String name, String building, int capacity, RoomKind kind) {
        return new Room(id, name, building, capacity, kind, true);
    }

    /** Returns a copy with the active flag set. */
    public Room withActive(boolean nowActive) {
        return new Room(id, name, building, capacity, kind, nowActive);
    }

    /** Can this room seat {@code attendees}? */
    public boolean fits(int attendees) {
        return attendees > 0 && attendees <= capacity;
    }

    /** Label used in listings, for example {@code Wean 5207 (Study room)}. */
    public String label() {
        return name + " (" + kind.displayName() + ")";
    }
}
