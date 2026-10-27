package edu.cmu.cs214.campusreserve.core.ports;

import edu.cmu.cs214.campusreserve.core.domain.User;
import java.util.List;
import java.util.Optional;

/** Where users live, from the booking domain's point of view. */
public interface UserStore {

    /** Inserts the user, or replaces the one already stored under its id. */
    void save(User user);

    /** The user with that id, if the store has one. */
    Optional<User> findById(String userId);

    /** Every user, ordered by id. */
    List<User> findAll();

    /** Does the store hold a user with that id? */
    default boolean exists(String userId) {
        return findById(userId).isPresent();
    }

    /** How many users the store holds. */
    int count();
}
