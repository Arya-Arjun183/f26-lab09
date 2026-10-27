package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.ports.RoomStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Rooms held in memory, ordered by id on the way out. */
public final class InMemoryRoomStore extends InMemoryRecordStore<Room> implements RoomStore {

    private static final Comparator<Room> BY_ID = Comparator.comparing(Room::id);

    public InMemoryRoomStore() {
        super(Room::id);
    }

    @Override
    public void save(Room room) {
        put(room);
    }

    @Override
    public Optional<Room> findById(String roomId) {
        return get(roomId);
    }

    @Override
    public List<Room> findAll() {
        return sorted(BY_ID);
    }

    @Override
    public List<Room> findActive() {
        return select(Room::active, BY_ID);
    }

    /** Every room in a building, active or not, ordered by id. */
    public List<Room> findByBuilding(String building) {
        return select(room -> room.building().equalsIgnoreCase(building), BY_ID);
    }

    /** Every active room that seats at least {@code attendees}, smallest first. */
    public List<Room> findFitting(int attendees) {
        return select(
            room -> room.active() && room.fits(attendees),
            Comparator.comparingInt(Room::capacity).thenComparing(Room::id));
    }

    /** Takes the room out of the store entirely. Meant for test setup and seeding. */
    public boolean remove(String roomId) {
        return delete(roomId);
    }
}
