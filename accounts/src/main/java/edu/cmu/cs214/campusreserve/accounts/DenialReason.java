package edu.cmu.cs214.campusreserve.accounts;

import java.util.Locale;

/**
 * Why a request was turned away. Callers that render an error page or a response
 * body switch on this instead of parsing a message string.
 */
public enum DenialReason {

    /** No credentials were presented at all. */
    MISSING_TOKEN("missing_token", "No credentials were presented."),

    /** A token was presented but no such token is on file. */
    INVALID_TOKEN("invalid_token", "The credentials were not recognized."),

    /** The token is on file but its lifetime has run out. */
    EXPIRED_TOKEN("expired_token", "The credentials have expired."),

    /** The token identifies a real account that has been switched off. */
    ACCOUNT_INACTIVE("account_inactive", "The account is not active."),

    /** The caller is known but does not hold the record it asked about. */
    NOT_OWNER("not_owner", "The account does not hold this record."),

    /** The operation is staff only and the caller is not staff. */
    ADMIN_REQUIRED("admin_required", "The operation requires an administrator.");

    private final String code;
    private final String message;

    DenialReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** The stable spelling used on the wire and in logs. */
    public String code() {
        return code;
    }

    /** A sentence safe to show to the caller. It never names another account. */
    public String message() {
        return message;
    }

    /**
     * Did the caller fail to prove who it is (as opposed to proving it and then
     * lacking permission)? The two groups usually map to different responses, so
     * the split lives here rather than in each caller.
     */
    public boolean isAuthenticationFailure() {
        return switch (this) {
            case MISSING_TOKEN, INVALID_TOKEN, EXPIRED_TOKEN, ACCOUNT_INACTIVE -> true;
            case NOT_OWNER, ADMIN_REQUIRED -> false;
        };
    }

    /**
     * The response status a caller should send for this reason: 401 when the
     * caller has not proved who it is, 403 when it has and still may not do this.
     * The mapping lives beside the reasons so that every front end answers the
     * same way.
     */
    public int suggestedHttpStatus() {
        return isAuthenticationFailure() ? 401 : 403;
    }

    /** The reason for a wire code. Case and surrounding whitespace are ignored. */
    public static DenialReason fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("denial code is required");
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (DenialReason reason : values()) {
            if (reason.code.equals(wanted)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown denial code: " + value);
    }
}
