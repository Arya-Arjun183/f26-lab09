package edu.cmu.cs214.campusreserve.accounts;

import edu.cmu.cs214.campusreserve.core.ports.UserStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every account the platform holds, indexed by id and by address. The directory
 * is the only place that decides an address belongs to one person: the record
 * itself cannot see the other accounts.
 *
 * <p>Instances are not safe for use from several threads at once.
 */
public final class AccountDirectory {

    private static final Comparator<Account> BY_ID = Comparator.comparing(Account::userId);

    private final Map<String, Account> byId = new HashMap<>();
    private final Map<String, String> idByEmailKey = new HashMap<>();

    /**
     * Adds an account.
     *
     * @return the stored account, which is the argument with its fields trimmed
     * @throws IllegalArgumentException if the id is already registered, or the
     *     address already belongs to a different id
     */
    public Account register(Account account) {
        Objects.requireNonNull(account, "account");
        if (byId.containsKey(account.userId())) {
            throw new IllegalArgumentException("An account is already registered under " + account.userId());
        }
        String emailKey = account.emailKey();
        String holder = idByEmailKey.get(emailKey);
        if (holder != null) {
            throw new IllegalArgumentException(
                "Email " + account.email() + " already belongs to " + holder);
        }
        byId.put(account.userId(), account);
        idByEmailKey.put(emailKey, account.userId());
        return account;
    }

    /** The account with that id, if the directory has one. */
    public Optional<Account> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(userId.trim()));
    }

    /** The account with that id, or a failure when there is none. */
    public Account require(String userId) {
        return find(userId).orElseThrow(
            () -> new IllegalArgumentException("No account is registered under " + userId));
    }

    /**
     * The account with that address. Case does not matter, so the address a
     * caller typed on a sign-in form finds the account it was registered with.
     */
    public Optional<Account> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String id = idByEmailKey.get(AccountRules.normalizeEmail(email));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /** Does the directory hold an account under that id? */
    public boolean contains(String userId) {
        return find(userId).isPresent();
    }

    /** Every account, ordered by id. */
    public List<Account> listAll() {
        List<Account> all = new ArrayList<>(byId.values());
        all.sort(BY_ID);
        return List.copyOf(all);
    }

    /** Every account holding that role, ordered by id. */
    public List<Account> listByRole(Role role) {
        Objects.requireNonNull(role, "role");
        return listAll().stream().filter(account -> account.role() == role).toList();
    }

    /** Every account that has not been switched off, ordered by id. */
    public List<Account> listActive() {
        return listAll().stream().filter(Account::active).toList();
    }

    /**
     * Switches the account off. Tokens already issued are left in place, but an
     * inactive account never authenticates, so those tokens stop being useful the
     * moment this returns. Calling it twice is harmless.
     *
     * @return the stored account after the change
     */
    public Account deactivate(String userId) {
        return replace(require(userId).withActive(false));
    }

    /**
     * Switches the account back on. Tokens issued before the account was switched
     * off work again if their own window has not run out.
     *
     * @return the stored account after the change
     */
    public Account reactivate(String userId) {
        return replace(require(userId).withActive(true));
    }

    /**
     * Moves the account to a different role.
     *
     * @return the stored account after the change
     */
    public Account assignRole(String userId, Role role) {
        Objects.requireNonNull(role, "role");
        return replace(require(userId).withRole(role));
    }

    /**
     * Puts a new display name on the account. The address is what identifies a
     * person here, so a name change touches no index.
     *
     * @return the stored account after the change
     */
    public Account rename(String userId, String newName) {
        return replace(require(userId).withName(newName));
    }

    /**
     * The accounts whose name or address contains that text, ordered by id. Case
     * does not matter. A blank query matches nothing rather than everything, so a
     * search box that has not been typed in yet does not dump the whole directory.
     */
    public List<Account> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        return listAll().stream()
            .filter(account -> account.name().toLowerCase(Locale.ROOT).contains(wanted)
                || account.emailKey().contains(wanted))
            .toList();
    }

    /** How many accounts the directory holds, active or not. */
    public int count() {
        return byId.size();
    }

    /** How many accounts have not been switched off. */
    public int countActive() {
        return listActive().size();
    }

    /**
     * Pushes every active account into the booking domain's user store as a core
     * user view. Accounts that have been switched off are left out, so the store
     * only ever names people who can still act. Existing entries under the same
     * id are replaced.
     *
     * @return how many accounts were written
     */
    public int syncInto(UserStore store) {
        Objects.requireNonNull(store, "store");
        List<Account> active = listActive();
        for (Account account : active) {
            store.save(account.toUser());
        }
        return active.size();
    }

    private Account replace(Account updated) {
        byId.put(updated.userId(), updated);
        return updated;
    }
}
