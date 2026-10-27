package edu.cmu.cs214.campusreserve.accounts.support;

import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.ports.UserStore;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** A user store held in a sorted map, used to watch what a sync writes. */
public final class RecordingUserStore implements UserStore {

    private final TreeMap<String, User> saved = new TreeMap<>();
    private int saveCalls;

    @Override
    public void save(User user) {
        saved.put(user.id(), user);
        saveCalls++;
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(saved.get(userId));
    }

    @Override
    public List<User> findAll() {
        return List.copyOf(saved.values());
    }

    @Override
    public int count() {
        return saved.size();
    }

    /** How many times save was called, including replacements of the same id. */
    public int saveCalls() {
        return saveCalls;
    }
}
