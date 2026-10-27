package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingStatus;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.ports.BookingStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bookings held in memory.
 *
 * <p>Every query comes back ordered by start time, then by id, so callers that
 * render a calendar do not have to sort. Cancelled bookings stay in the store as
 * history; the {@code active} queries filter them out.
 *
 * <p>{@link #findConflicts(String, TimeInterval)} settles the room and the
 * window in the same pass over the records: availability calls it once per
 * window, and building the room's whole calendar first only to drop most of it
 * again is the part worth skipping. Intervals are half-open, so a booking that
 * ends exactly when the window opens is not part of the answer.
 */
public final class InMemoryBookingStore extends InMemoryRecordStore<Booking> implements BookingStore {

    private static final Comparator<Booking> BY_START =
        Comparator.comparing(Booking::interval).thenComparing(Booking::id);

    public InMemoryBookingStore() {
        super(Booking::id);
    }

    @Override
    public void save(Booking booking) {
        put(booking);
    }

    @Override
    public Optional<Booking> findById(String bookingId) {
        return get(bookingId);
    }

    @Override
    public List<Booking> findByRoom(String roomId) {
        return select(booking -> booking.roomId().equals(roomId), BY_START);
    }

    @Override
    public List<Booking> findActiveByRoom(String roomId) {
        return select(booking -> booking.roomId().equals(roomId) && booking.holdsRoom(), BY_START);
    }

    @Override
    public List<Booking> findConflicts(String roomId, TimeInterval window) {
        Instant windowStart = window.start();
        Instant windowEnd = window.end();
        return select(
            booking -> booking.roomId().equals(roomId)
                && booking.holdsRoom()
                && booking.interval().start().isBefore(windowEnd)
                && windowStart.isBefore(booking.interval().end()),
            BY_START);
    }

    @Override
    public List<Booking> findByUser(String userId) {
        return select(booking -> booking.userId().equals(userId), BY_START);
    }

    @Override
    public List<Booking> findActiveByUser(String userId) {
        return select(booking -> booking.userId().equals(userId) && booking.holdsRoom(), BY_START);
    }

    @Override
    public List<Booking> findAll() {
        return sorted(BY_START);
    }

    /** Every booking in that status, ordered by start. */
    public List<Booking> findByStatus(BookingStatus status) {
        return select(booking -> booking.status() == status, BY_START);
    }

    /** Bookings whose interval starts at or after {@code from}, ordered by start. */
    public List<Booking> findStartingFrom(Instant from) {
        return select(booking -> !booking.interval().start().isBefore(from), BY_START);
    }

    /** Active bookings in the room that start at or after {@code from}. */
    public List<Booking> findUpcomingByRoom(String roomId, Instant from) {
        return select(
            booking -> booking.roomId().equals(roomId)
                && booking.holdsRoom()
                && !booking.interval().start().isBefore(from),
            BY_START);
    }

    /** How many bookings are in that status. */
    public int countByStatus(BookingStatus status) {
        return findByStatus(status).size();
    }

    /** Takes the booking out of the store. Meant for test setup and seeding. */
    public boolean remove(String bookingId) {
        return delete(bookingId);
    }
}
