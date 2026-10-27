package edu.cmu.cs214.campusreserve.notifications;

import edu.cmu.cs214.campusreserve.core.domain.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A directory held in memory, keyed by user id.
 *
 * <p>Adding the same user id twice replaces the entry, so a refreshed address
 * wins over the one already stored.
 */
public final class InMemoryUserDirectory implements UserDirectory {

    private final Map<String, Recipient> byUserId = new ConcurrentHashMap<>();

    /** A directory with nobody in it. */
    public static InMemoryUserDirectory empty() {
        return new InMemoryUserDirectory();
    }

    /**
     * Copies contact details out of domain users. The directory does not hold on
     * to the list, so later changes to it are not visible here.
     */
    public static InMemoryUserDirectory of(List<User> users) {
        Objects.requireNonNull(users, "users");
        InMemoryUserDirectory directory = new InMemoryUserDirectory();
        for (User user : users) {
            directory.add(Recipient.of(user));
        }
        return directory;
    }

    /** Adds or replaces an entry. Returns this directory so calls can chain. */
    public InMemoryUserDirectory add(Recipient recipient) {
        Objects.requireNonNull(recipient, "recipient");
        byUserId.put(recipient.userId(), recipient);
        return this;
    }

    /** Adds or replaces an entry from a domain user. */
    public InMemoryUserDirectory add(User user) {
        return add(Recipient.of(user));
    }

    /** Drops an entry. Reports whether there was one to drop. */
    public boolean remove(String userId) {
        return userId != null && byUserId.remove(userId) != null;
    }

    @Override
    public Optional<Recipient> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byUserId.get(userId.trim()));
    }

    /** How many people the directory holds. */
    public int size() {
        return byUserId.size();
    }
}
