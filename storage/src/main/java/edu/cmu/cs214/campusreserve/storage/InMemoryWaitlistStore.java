package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import edu.cmu.cs214.campusreserve.core.ports.WaitlistStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Waitlist entries held in memory.
 *
 * <p>Every query comes back in arrival order, which is what makes promotion
 * first in, first out. The arrival counter increases across the whole platform
 * and never reuses a number, so two entries can always be ordered even when they
 * arrive in the same minute.
 */
public final class InMemoryWaitlistStore extends InMemoryRecordStore<WaitlistEntry> implements WaitlistStore {

    private static final Comparator<WaitlistEntry> BY_ARRIVAL =
        Comparator.comparingLong(WaitlistEntry::sequence);

    private final AtomicLong sequence = new AtomicLong();

    public InMemoryWaitlistStore() {
        super(WaitlistEntry::id);
    }

    @Override
    public void save(WaitlistEntry entry) {
        put(entry);
    }

    @Override
    public Optional<WaitlistEntry> findById(String entryId) {
        return get(entryId);
    }

    @Override
    public List<WaitlistEntry> findByRoom(String roomId) {
        return select(entry -> entry.roomId().equals(roomId), BY_ARRIVAL);
    }

    @Override
    public List<WaitlistEntry> findByUser(String userId) {
        return select(entry -> entry.userId().equals(userId), BY_ARRIVAL);
    }

    @Override
    public List<WaitlistEntry> findAll() {
        return sorted(BY_ARRIVAL);
    }

    @Override
    public boolean remove(String entryId) {
        return delete(entryId);
    }

    @Override
    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    /** The entry that has waited longest for the room, if any is waiting. */
    public Optional<WaitlistEntry> peekRoom(String roomId) {
        return findByRoom(roomId).stream().findFirst();
    }

    /** How many people are waiting for the room. */
    public int countForRoom(String roomId) {
        return findByRoom(roomId).size();
    }

    /** How many arrival numbers have been handed out. */
    public long issuedSequences() {
        return sequence.get();
    }
}
