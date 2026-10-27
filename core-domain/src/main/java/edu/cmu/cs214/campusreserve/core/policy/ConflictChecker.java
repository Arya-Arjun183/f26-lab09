package edu.cmu.cs214.campusreserve.core.policy;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether a candidate interval collides with bookings that already hold
 * a room.
 *
 * <p>The class holds no state and no time semantics of its own. Every decision
 * here comes from {@link TimeInterval#overlaps(TimeInterval)}, which is the only
 * overlap implementation in the platform. Cancelled bookings are skipped because
 * they no longer hold the room.
 */
public final class ConflictChecker {

    /** Does any booking in {@code existing} hold the room over {@code candidate}? */
    public boolean hasConflict(Collection<Booking> existing, TimeInterval candidate) {
        return firstConflict(existing, candidate).isPresent();
    }

    /** The earliest booking that collides with {@code candidate}, if there is one. */
    public Optional<Booking> firstConflict(Collection<Booking> existing, TimeInterval candidate) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(candidate, "candidate");
        return existing.stream()
            .filter(Booking::holdsRoom)
            .filter(booking -> booking.interval().overlaps(candidate))
            .min(Comparator.comparing(booking -> booking.interval().start()));
    }

    /** Every booking that collides with {@code candidate}, ordered by start. */
    public List<Booking> conflicts(Collection<Booking> existing, TimeInterval candidate) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(candidate, "candidate");
        List<Booking> found = new ArrayList<>();
        for (Booking booking : existing) {
            if (booking.holdsRoom() && booking.interval().overlaps(candidate)) {
                found.add(booking);
            }
        }
        found.sort(Comparator.comparing(booking -> booking.interval()));
        return List.copyOf(found);
    }

    /**
     * Do any two bookings in {@code bookings} collide with each other? The store
     * should never hold such a pair; the check exists so tests and the admin CLI
     * can assert it.
     */
    public boolean containsMutualConflict(List<Booking> bookings) {
        Objects.requireNonNull(bookings, "bookings");
        List<Booking> held = new ArrayList<>(bookings.stream().filter(Booking::holdsRoom).toList());
        held.sort(Comparator.comparing(Booking::interval));
        for (int i = 1; i < held.size(); i++) {
            if (held.get(i - 1).interval().overlaps(held.get(i).interval())) {
                return true;
            }
        }
        return false;
    }
}
