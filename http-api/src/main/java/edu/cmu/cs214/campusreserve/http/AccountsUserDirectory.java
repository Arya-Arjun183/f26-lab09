package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.accounts.AccountDirectory;
import edu.cmu.cs214.campusreserve.notifications.Recipient;
import edu.cmu.cs214.campusreserve.notifications.UserDirectory;
import java.util.Objects;
import java.util.Optional;

/**
 * Lets the notifications module address people without knowing that the accounts
 * module exists.
 *
 * <p>Notifications declares a narrow {@code UserDirectory} port. This adapter is
 * the only place the two modules meet, and it lives here because http-api is the
 * module that depends on both.
 *
 * <p>An inactive account gets no notifications: there is nobody there to read
 * them.
 */
public final class AccountsUserDirectory implements UserDirectory {

    private final AccountDirectory accounts;

    public AccountsUserDirectory(AccountDirectory accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    public Optional<Recipient> find(String userId) {
        return accounts.find(userId)
            .filter(Account::active)
            .map(account -> new Recipient(account.userId(), account.name(), account.email()));
    }
}
