package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.ports.UserStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Users held in memory, ordered by id on the way out. */
public final class InMemoryUserStore extends InMemoryRecordStore<User> implements UserStore {

    private static final Comparator<User> BY_ID = Comparator.comparing(User::id);

    public InMemoryUserStore() {
        super(User::id);
    }

    @Override
    public void save(User user) {
        put(user);
    }

    @Override
    public Optional<User> findById(String userId) {
        return get(userId);
    }

    @Override
    public List<User> findAll() {
        return sorted(BY_ID);
    }

    /** The user with that address, if the store has one. Case is ignored. */
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String wanted = email.trim();
        return values().stream().filter(user -> user.email().equalsIgnoreCase(wanted)).findFirst();
    }

    /** Takes the user out of the store. Meant for test setup and seeding. */
    public boolean remove(String userId) {
        return delete(userId);
    }
}
