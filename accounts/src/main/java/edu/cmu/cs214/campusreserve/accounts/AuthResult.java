package edu.cmu.cs214.campusreserve.accounts;

import java.util.Objects;
import java.util.Optional;

/**
 * The answer to one access check: either the account that passed it, or the
 * reason it failed. The type is sealed, so a caller that switches over it is
 * told by the compiler when it has missed a case.
 */
public sealed interface AuthResult {

    /** The check passed, and this is who the caller is acting as. */
    record Allowed(Account account) implements AuthResult {
        public Allowed {
            Objects.requireNonNull(account, "account");
        }
    }

    /**
     * The check failed.
     *
     * @param reason the coded reason, for the caller's response
     * @param detail a short note naming what was checked, safe to log
     */
    record Denied(DenialReason reason, String detail) implements AuthResult {
        public Denied {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? "" : detail;
        }
    }

    /** Did the check pass? */
    default boolean isAllowed() {
        return this instanceof Allowed;
    }

    /** The account that passed the check, or empty when it failed. */
    default Optional<Account> allowedAccount() {
        return this instanceof Allowed allowed ? Optional.of(allowed.account()) : Optional.empty();
    }

    /** Why the check failed, or empty when it passed. */
    default Optional<DenialReason> denialReason() {
        return this instanceof Denied denied ? Optional.of(denied.reason()) : Optional.empty();
    }

    /**
     * The account that passed the check.
     *
     * @throws AccessDeniedException if the check failed
     */
    default Account orElseThrow() {
        return switch (this) {
            case Allowed allowed -> allowed.account();
            case Denied denied -> throw new AccessDeniedException(denied.reason(), denied.detail());
        };
    }

    /** One line for a log: who passed, or which check failed and on what. */
    default String describe() {
        return switch (this) {
            case Allowed allowed -> "allowed " + allowed.account().userId();
            case Denied denied -> "denied " + denied.reason().code() + " (" + denied.detail() + ")";
        };
    }
}
