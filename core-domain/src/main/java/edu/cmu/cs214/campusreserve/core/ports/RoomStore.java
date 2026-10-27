package edu.cmu.cs214.campusreserve.core.ports;

import edu.cmu.cs214.campusreserve.core.domain.Room;
import java.util.List;
import java.util.Optional;

/**
 * Where rooms live. The core domain declares the port; the storage module ships
 * the implementation.
 */
public interface RoomStore {

    /** Inserts the room, or replaces the one already stored under its id. */
    void save(Room room);

    /** The room with that id, if the store has one. */
    Optional<Room> findById(String roomId);

    /** Every room, ordered by id. */
    List<Room> findAll();

    /** Every room currently taking bookings, ordered by id. */
    List<Room> findActive();

    /** How many rooms the store holds. */
    int count();
}
