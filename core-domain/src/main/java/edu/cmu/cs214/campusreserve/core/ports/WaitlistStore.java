package edu.cmu.cs214.campusreserve.core.ports;

import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import java.util.List;
import java.util.Optional;

/**
 * Where waitlist entries live.
 *
 * <p>The store hands out the arrival sequence and returns a room's entries in
 * that order, which is what makes promotion first in, first out. Only
 * {@code BookingService} writes through this port.
 */
public interface WaitlistStore {

    /** Inserts the entry, or replaces the one already stored under its id. */
    void save(WaitlistEntry entry);

    /** The entry with that id, if the store has one. */
    Optional<WaitlistEntry> findById(String entryId);

    /** The room's entries in arrival order. */
    List<WaitlistEntry> findByRoom(String roomId);

    /** The user's entries in arrival order. */
    List<WaitlistEntry> findByUser(String userId);

    /** Every entry in arrival order. */
    List<WaitlistEntry> findAll();

    /** Drops the entry. Returns whether there was one to drop. */
    boolean remove(String entryId);

    /** The next arrival number. Numbers increase and are never reused. */
    long nextSequence();

    /** How many entries the store holds. */
    int count();
}
