package edu.cmu.cs214.campusreserve.core.support;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import edu.cmu.cs214.campusreserve.core.ports.BookingStore;
import edu.cmu.cs214.campusreserve.core.ports.RoomStore;
import edu.cmu.cs214.campusreserve.core.ports.UserStore;
import edu.cmu.cs214.campusreserve.core.ports.WaitlistStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Map-backed store implementations used by the core-domain tests. The storage
 * module ships the implementations the platform runs on; these exist so the
 * domain suite can run without depending on it.
 */
public final class InMemoryStores {

    private InMemoryStores() {
    }

    /** Rooms in a map. */
    public static final class Rooms implements RoomStore {
        private final Map<String, Room> rooms = new LinkedHashMap<>();

        @Override
        public void save(Room room) {
            rooms.put(room.id(), room);
        }

        @Override
        public Optional<Room> findById(String roomId) {
            return Optional.ofNullable(rooms.get(roomId));
        }

        @Override
        public List<Room> findAll() {
            return rooms.values().stream().sorted(Comparator.comparing(Room::id)).toList();
        }

        @Override
        public List<Room> findActive() {
            return findAll().stream().filter(Room::active).toList();
        }

        @Override
        public int count() {
            return rooms.size();
        }
    }

    /** Users in a map. */
    public static final class Users implements UserStore {
        private final Map<String, User> users = new LinkedHashMap<>();

        @Override
        public void save(User user) {
            users.put(user.id(), user);
        }

        @Override
        public Optional<User> findById(String userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public List<User> findAll() {
            return users.values().stream().sorted(Comparator.comparing(User::id)).toList();
        }

        @Override
        public int count() {
            return users.size();
        }
    }

    /** Bookings in a map. */
    public static final class Bookings implements BookingStore {
        private final Map<String, Booking> bookings = new LinkedHashMap<>();

        @Override
        public void save(Booking booking) {
            bookings.put(booking.id(), booking);
        }

        @Override
        public Optional<Booking> findById(String bookingId) {
            return Optional.ofNullable(bookings.get(bookingId));
        }

        @Override
        public List<Booking> findByRoom(String roomId) {
            return sorted(bookings.values().stream().filter(b -> b.roomId().equals(roomId)).toList());
        }

        @Override
        public List<Booking> findActiveByRoom(String roomId) {
            return findByRoom(roomId).stream().filter(Booking::holdsRoom).toList();
        }

        @Override
        public List<Booking> findConflicts(String roomId, TimeInterval window) {
            return findActiveByRoom(roomId).stream()
                .filter(booking -> booking.interval().overlaps(window))
                .toList();
        }

        @Override
        public List<Booking> findByUser(String userId) {
            return sorted(bookings.values().stream().filter(b -> b.userId().equals(userId)).toList());
        }

        @Override
        public List<Booking> findActiveByUser(String userId) {
            return findByUser(userId).stream().filter(Booking::holdsRoom).toList();
        }

        @Override
        public List<Booking> findAll() {
            return sorted(List.copyOf(bookings.values()));
        }

        @Override
        public int count() {
            return bookings.size();
        }

        private static List<Booking> sorted(List<Booking> input) {
            List<Booking> copy = new ArrayList<>(input);
            copy.sort(Comparator.comparing(Booking::interval).thenComparing(Booking::id));
            return List.copyOf(copy);
        }
    }

    /** Waitlist entries in a map, handing out arrival numbers. */
    public static final class Waitlist implements WaitlistStore {
        private final Map<String, WaitlistEntry> entries = new LinkedHashMap<>();
        private long sequence;

        @Override
        public void save(WaitlistEntry entry) {
            entries.put(entry.id(), entry);
        }

        @Override
        public Optional<WaitlistEntry> findById(String entryId) {
            return Optional.ofNullable(entries.get(entryId));
        }

        @Override
        public List<WaitlistEntry> findByRoom(String roomId) {
            return inArrivalOrder(entries.values().stream().filter(e -> e.roomId().equals(roomId)).toList());
        }

        @Override
        public List<WaitlistEntry> findByUser(String userId) {
            return inArrivalOrder(entries.values().stream().filter(e -> e.userId().equals(userId)).toList());
        }

        @Override
        public List<WaitlistEntry> findAll() {
            return inArrivalOrder(List.copyOf(entries.values()));
        }

        @Override
        public boolean remove(String entryId) {
            return entries.remove(entryId) != null;
        }

        @Override
        public long nextSequence() {
            return ++sequence;
        }

        @Override
        public int count() {
            return entries.size();
        }

        private static List<WaitlistEntry> inArrivalOrder(List<WaitlistEntry> input) {
            List<WaitlistEntry> copy = new ArrayList<>(input);
            copy.sort(Comparator.comparingLong(WaitlistEntry::sequence));
            return List.copyOf(copy);
        }
    }
}
