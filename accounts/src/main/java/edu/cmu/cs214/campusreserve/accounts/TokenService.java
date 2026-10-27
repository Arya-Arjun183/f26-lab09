package edu.cmu.cs214.campusreserve.accounts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mints tokens, hands back the account behind one, and throws tokens away. The
 * clock is injected so that a caller can decide what "now" means.
 *
 * <p>Expiry is checked on every read, not on a timer. A token whose window has
 * run out stops authenticating right away; {@link #purgeExpired()} only reclaims
 * the memory those entries were using.
 *
 * <p>Instances are not safe for use from several threads at once.
 */
public final class TokenService {

    /** How many live tokens one account may hold unless the caller says otherwise. */
    public static final int DEFAULT_SESSION_LIMIT = 10;

    private final AccountDirectory directory;
    private final Clock clock;
    private final TokenMinter minter;
    private final int sessionLimit;
    private final Map<String, Token> byValue = new LinkedHashMap<>();

    /**
     * A service with the default session limit.
     *
     * @param directory where the accounts behind the tokens live
     * @param clock the source of the current instant
     * @param minter where token strings come from
     */
    public TokenService(AccountDirectory directory, Clock clock, TokenMinter minter) {
        this(directory, clock, minter, DEFAULT_SESSION_LIMIT);
    }

    /**
     * @param directory where the accounts behind the tokens live
     * @param clock the source of the current instant
     * @param minter where token strings come from
     * @param sessionLimit how many live tokens one account may hold at once, at
     *     least one; issuing past the limit drops that account's oldest live
     *     token rather than refusing
     */
    public TokenService(AccountDirectory directory, Clock clock, TokenMinter minter, int sessionLimit) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minter = Objects.requireNonNull(minter, "minter");
        if (sessionLimit < 1) {
            throw new IllegalArgumentException("An account needs at least one session: " + sessionLimit);
        }
        this.sessionLimit = sessionLimit;
    }

    /** How many live tokens one account may hold at once. */
    public int sessionLimit() {
        return sessionLimit;
    }

    /**
     * Mints a token for an account that is registered and active.
     *
     * @throws IllegalArgumentException if no account holds that id, or the
     *     lifetime is not positive or runs past the platform maximum
     * @throws IllegalStateException if the account has been switched off, or the
     *     minter handed back a value that is already in use
     */
    public Token issue(String userId, Duration lifetime) {
        Account account = directory.require(userId);
        if (!account.active()) {
            throw new IllegalStateException("Account is not active: " + account.userId());
        }
        Duration window = AccountRules.requireLifetime(lifetime);
        Instant now = clock.instant();
        Token token = new Token(minter.mint(account.userId(), now), account.userId(), now, now.plus(window));
        if (byValue.putIfAbsent(token.value(), token) != null) {
            throw new IllegalStateException("The minter reused a token value: " + token.maskedValue());
        }
        dropOldestPastLimit(account.userId());
        return token;
    }

    /**
     * Swaps a still-good token for a fresh one and throws the old one away, which
     * is how a long sign-in stays alive without widening the window on any single
     * value. The old token stops working whether or not the caller keeps a copy.
     *
     * @return the new token, or empty when the presented token would not have
     *     authenticated in the first place
     */
    public Optional<Token> refresh(String tokenValue, Duration lifetime) {
        Optional<Account> holder = authenticate(tokenValue);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        revoke(tokenValue);
        return Optional.of(issue(holder.get().userId(), lifetime));
    }

    /**
     * The token with that value, expired or not. Callers that need to tell an
     * unknown value apart from a stale one read this and check the window
     * themselves; callers that only want the account use
     * {@link #authenticate(String)}.
     */
    public Optional<Token> find(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byValue.get(tokenValue.trim()));
    }

    /**
     * The account behind that token value, but only when the token is still
     * inside its window and the account is still active. Every other case, from
     * an unknown value to a switched-off account, comes back empty.
     */
    public Optional<Account> authenticate(String tokenValue) {
        Instant now = clock.instant();
        return find(tokenValue)
            .filter(token -> !token.isExpiredAt(now))
            .flatMap(token -> directory.find(token.userId()))
            .filter(Account::active);
    }

    /**
     * Throws one token away.
     *
     * @return true if a token was removed, false if the value was not on file
     */
    public boolean revoke(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        return byValue.remove(tokenValue.trim()) != null;
    }

    /**
     * Throws away every token an account holds, expired ones included. This is
     * the call to make when someone signs out everywhere.
     *
     * @return how many tokens were removed
     */
    public int revokeAllFor(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        String wanted = userId.trim();
        int before = byValue.size();
        byValue.values().removeIf(token -> token.userId().equals(wanted));
        return before - byValue.size();
    }

    /**
     * Drops every token whose window has run out. Nothing about who can act
     * changes: those tokens had already stopped working.
     *
     * @return how many tokens were dropped
     */
    public int purgeExpired() {
        Instant now = clock.instant();
        int before = byValue.size();
        byValue.values().removeIf(token -> token.isExpiredAt(now));
        return before - byValue.size();
    }

    /** How many tokens are still inside their window right now. */
    public int activeTokenCount() {
        Instant now = clock.instant();
        return (int) byValue.values().stream().filter(token -> !token.isExpiredAt(now)).count();
    }

    /**
     * How many tokens are held in memory, including expired ones that have not
     * been purged yet. This is the number a purge changes.
     */
    public int storedTokenCount() {
        return byValue.size();
    }

    /**
     * The tokens that account can still use, oldest first. Expired tokens are
     * left out whether or not a purge has run.
     */
    public List<Token> tokensFor(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String wanted = userId.trim();
        Instant now = clock.instant();
        return byValue.values().stream()
            .filter(token -> token.userId().equals(wanted))
            .filter(token -> !token.isExpiredAt(now))
            .toList();
    }

    /**
     * The live sessions that account holds, oldest first, in the masked form that
     * is safe to show the person signed in.
     */
    public List<SessionSummary> sessionsFor(String userId) {
        Instant now = clock.instant();
        return tokensFor(userId).stream().map(token -> SessionSummary.of(token, now)).toList();
    }

    private void dropOldestPastLimit(String userId) {
        List<Token> live = tokensFor(userId);
        int excess = live.size() - sessionLimit;
        for (int i = 0; i < excess; i++) {
            byValue.remove(live.get(i).value());
        }
    }
}
