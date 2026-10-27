package edu.cmu.cs214.campusreserve.core.ports;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.List;
import java.util.Optional;

/**
 * Where bookings live. Cancelled bookings stay in the store; the {@code active}
 * queries filter them out for callers that only care about held rooms.
 */
public interface BookingStore {

    /** Inserts the booking, or replaces the one already stored under its id. */
    void save(Booking booking);

    /** The booking with that id, if the store has one. */
    Optional<Booking> findById(String bookingId);

    /** Every booking for the room, cancelled ones included, ordered by start. */
    List<Booking> findByRoom(String roomId);

    /** Every booking for the room that still holds it, ordered by start. */
    List<Booking> findActiveByRoom(String roomId);

    /**
     * The room's held bookings that run over part of {@code window}, ordered by
     * start.
     *
     * <p>Availability asks this room by room and window by window, often many
     * times for one page. A store answers it directly instead of handing back
     * the room's whole calendar for the caller to narrow.
     */
    List<Booking> findConflicts(String roomId, TimeInterval window);

    /** Every booking for the user, cancelled ones included, ordered by start. */
    List<Booking> findByUser(String userId);

    /** Every booking for the user that still holds a room, ordered by start. */
    List<Booking> findActiveByUser(String userId);

    /** Every booking in the store, ordered by start. */
    List<Booking> findAll();

    /** How many bookings the store holds, cancelled ones included. */
    int count();
}
