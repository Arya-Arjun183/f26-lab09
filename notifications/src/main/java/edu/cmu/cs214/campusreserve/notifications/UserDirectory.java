package edu.cmu.cs214.campusreserve.notifications;

import java.util.Optional;

/**
 * Where this module gets contact details.
 *
 * <p>This is the whole of what notifications need to know about people. Whoever
 * owns accounts implements it, so nothing here has to depend on an account
 * store, a session, or a password.
 *
 * <p>A lookup that finds nobody returns an empty {@link Optional}. It is not an
 * error: a user can be removed while events about their old bookings are still
 * in flight.
 */
@FunctionalInterface
public interface UserDirectory {

    /** Contact details for a user id, or empty if the directory has none. */
    Optional<Recipient> find(String userId);

    /** A directory that knows nobody, for callers that want messages dropped. */
    static UserDirectory empty() {
        return userId -> Optional.empty();
    }
}
