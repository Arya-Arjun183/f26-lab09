package edu.cmu.cs214.campusreserve.core.service;

import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.ports.UserStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The users the booking domain knows about. Roles, tokens, and everything else
 * about a person live in the accounts module; the domain only needs to know that
 * a booker exists and how to address them.
 */
public final class UserRegistry {

    private final UserStore users;

    public UserRegistry(UserStore users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    /** Adds a user, or replaces the one already under that id. */
    public void register(User user) {
        users.save(Objects.requireNonNull(user, "user"));
    }

    /** The user with that id, if the registry has one. */
    public Optional<User> find(String userId) {
        return users.findById(userId);
    }

    /** The user with that id, or a failure when there is none. */
    public User require(String userId) {
        return users.findById(userId).orElseThrow(
            () -> new IllegalArgumentException(RejectionReason.USER_NOT_FOUND.message() + " " + userId));
    }

    /** Does the registry know this user? */
    public boolean exists(String userId) {
        return users.findById(userId).isPresent();
    }

    /** Every user the registry holds. */
    public List<User> listAll() {
        return users.findAll();
    }

    /** The user with that address, if the registry has one. */
    public Optional<User> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        return users.findAll().stream()
            .filter(user -> user.email().equalsIgnoreCase(email.trim()))
            .findFirst();
    }

    /** How many users the registry holds. */
    public int count() {
        return users.count();
    }
}
