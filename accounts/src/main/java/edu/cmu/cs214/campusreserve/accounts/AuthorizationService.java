package edu.cmu.cs214.campusreserve.accounts;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The access checks a caller runs before it does anything on someone's behalf.
 * Three questions get answered here: who is this, may they touch this record,
 * and are they staff.
 *
 * <p>The denial reasons are specific on purpose. An unknown token and a stale
 * one come back as different reasons, because a caller holding a stale token
 * should sign in again while a caller holding an unknown one has a bug or is
 * guessing. Neither reason ever names another account.
 */
public final class AuthorizationService {

    private final AccountDirectory directory;
    private final TokenService tokens;
    private final Clock clock;

    /**
     * @param directory where the accounts live
     * @param tokens where the tokens live
     * @param clock the source of the current instant, which decides expiry
     */
    public AuthorizationService(AccountDirectory directory, TokenService tokens, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Works out who a raw token value belongs to. The value is the token itself,
     * with no scheme in front of it.
     */
    public AuthResult authenticate(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return new AuthResult.Denied(DenialReason.MISSING_TOKEN, "no token value");
        }
        Optional<Token> found = tokens.find(tokenValue);
        if (found.isEmpty()) {
            return new AuthResult.Denied(DenialReason.INVALID_TOKEN, "token " + Token.mask(tokenValue));
        }
        Token token = found.get();
        Instant now = clock.instant();
        if (token.isExpiredAt(now)) {
            return new AuthResult.Denied(DenialReason.EXPIRED_TOKEN,
                "token " + token.maskedValue() + " expired at " + token.expiresAt());
        }
        Optional<Account> account = directory.find(token.userId());
        if (account.isEmpty()) {
            // The account was dropped while its token was still on file.
            return new AuthResult.Denied(DenialReason.INVALID_TOKEN, "token " + token.maskedValue());
        }
        if (!account.get().active()) {
            return new AuthResult.Denied(DenialReason.ACCOUNT_INACTIVE, "account " + token.userId());
        }
        return new AuthResult.Allowed(account.get());
    }

    /**
     * Works out who an {@code Authorization} header value belongs to. A missing
     * header is denied as missing credentials; a header the platform cannot read,
     * such as one using another scheme, is denied as invalid.
     */
    public AuthResult authenticateHeader(String headerValue) {
        Optional<String> presented;
        try {
            presented = AccountRules.bearerToken(headerValue);
        } catch (IllegalArgumentException malformed) {
            return new AuthResult.Denied(DenialReason.INVALID_TOKEN, malformed.getMessage());
        }
        if (presented.isEmpty()) {
            return new AuthResult.Denied(DenialReason.MISSING_TOKEN, "no Authorization header");
        }
        return authenticate(presented.get());
    }

    /**
     * May this account act on a record held by that user id? Owners may, and
     * administrators may act on anyone's.
     */
    public boolean mayActFor(Account account, String ownerUserId) {
        Objects.requireNonNull(account, "account");
        return account.canAdminister() || account.owns(ownerUserId);
    }

    /**
     * Checks an already authenticated account against the holder of a record.
     *
     * @return the account when it may act, otherwise a {@code NOT_OWNER} denial
     */
    public AuthResult checkOwner(Account account, String ownerUserId) {
        if (!mayActFor(account, ownerUserId)) {
            return new AuthResult.Denied(DenialReason.NOT_OWNER,
                "account " + account.userId() + " on a record held by " + ownerUserId);
        }
        return new AuthResult.Allowed(account);
    }

    /**
     * Checks an already authenticated account for staff rights.
     *
     * @return the account when it administers, otherwise an {@code ADMIN_REQUIRED}
     *     denial
     */
    public AuthResult checkAdmin(Account account) {
        Objects.requireNonNull(account, "account");
        if (!account.canAdminister()) {
            return new AuthResult.Denied(DenialReason.ADMIN_REQUIRED,
                "account " + account.userId() + " holds role " + account.role().code());
        }
        return new AuthResult.Allowed(account);
    }

    /**
     * The whole check for an operation on one person's record: read the header,
     * then confirm the caller holds the record or administers. The first failure
     * is the one reported, so a caller with no credentials hears about that
     * rather than about ownership.
     */
    public AuthResult authorizeOwner(String headerValue, String ownerUserId) {
        AuthResult identity = authenticateHeader(headerValue);
        return switch (identity) {
            case AuthResult.Denied denied -> denied;
            case AuthResult.Allowed allowed -> checkOwner(allowed.account(), ownerUserId);
        };
    }

    /** The whole check for a staff only operation: read the header, then require staff. */
    public AuthResult authorizeAdmin(String headerValue) {
        AuthResult identity = authenticateHeader(headerValue);
        return switch (identity) {
            case AuthResult.Denied denied -> denied;
            case AuthResult.Allowed allowed -> checkAdmin(allowed.account());
        };
    }
}
