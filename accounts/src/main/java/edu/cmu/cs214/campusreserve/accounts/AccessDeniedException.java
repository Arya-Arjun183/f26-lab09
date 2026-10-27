package edu.cmu.cs214.campusreserve.accounts;

import java.util.Objects;

/**
 * Thrown when a caller insists on the account behind a check that came back
 * denied. Callers that would rather branch than catch read the
 * {@link AuthResult} instead.
 */
public final class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DenialReason reason;
    private final String detail;

    /**
     * @param reason why the request was turned away
     * @param detail a short note naming what was checked, safe to log
     */
    public AccessDeniedException(DenialReason reason, String detail) {
        super(Objects.requireNonNull(reason, "reason").code() + ": " + detail);
        this.reason = reason;
        this.detail = detail == null ? "" : detail;
    }

    /** Why the request was turned away. */
    public DenialReason reason() {
        return reason;
    }

    /** The short note about what was checked. */
    public String detail() {
        return detail;
    }
}
