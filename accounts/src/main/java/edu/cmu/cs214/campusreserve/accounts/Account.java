package edu.cmu.cs214.campusreserve.accounts;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import edu.cmu.cs214.campusreserve.core.domain.User;
import java.time.Instant;
import java.util.Objects;

/**
 * A person this platform knows about, with everything the booking domain does
 * not need: the role, whether the account is switched on, and when it was made.
 * The address keeps the case it was registered with; lookups go through
 * {@link AccountRules#normalizeEmail(String)} instead of changing what is stored.
 *
 * @param userId the id shared with the booking domain
 * @param name the display name
 * @param email the address to reach this person at
 * @param role what this account may do
 * @param active false once the account has been switched off
 * @param createdAt when the account was registered
 */
public record Account(String userId, String name, String email, Role role, boolean active, Instant createdAt) {

    public Account {
        userId = Identifiers.require(userId, "user id");
        name = Identifiers.requireText(name, "account name");
        email = AccountRules.requireEmail(email);
        role = Objects.requireNonNull(role, "role");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new active member account. */
    public static Account member(String userId, String name, String email, Instant createdAt) {
        return new Account(userId, name, email, Role.MEMBER, true, createdAt);
    }

    /** A new active administrator account. */
    public static Account admin(String userId, String name, String email, Instant createdAt) {
        return new Account(userId, name, email, Role.ADMIN, true, createdAt);
    }

    /** The same account under a different role. */
    public Account withRole(Role newRole) {
        Objects.requireNonNull(newRole, "role");
        return newRole == role ? this : new Account(userId, name, email, newRole, active, createdAt);
    }

    /** The same account under a different display name. */
    public Account withName(String newName) {
        String checked = Identifiers.requireText(newName, "account name");
        return checked.equals(name) ? this : new Account(userId, checked, email, role, active, createdAt);
    }

    /** The same account switched on or off. */
    public Account withActive(boolean nowActive) {
        return nowActive == active ? this : new Account(userId, name, email, role, nowActive, createdAt);
    }

    /**
     * The booking domain's view of this person. Roles and the active flag stay
     * behind: a booking only needs an id, a name, and an address.
     */
    public User toUser() {
        return new User(userId, name, email);
    }

    /** The lookup form of this account's address. */
    public String emailKey() {
        return AccountRules.normalizeEmail(email);
    }

    /** Does this account reach the administrative operations? */
    public boolean canAdminister() {
        return role.canAdminister();
    }

    /** Does this account hold the record owned by that user id? */
    public boolean owns(String ownerUserId) {
        return userId.equals(ownerUserId);
    }
}
