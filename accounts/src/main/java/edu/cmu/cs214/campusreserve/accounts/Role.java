package edu.cmu.cs214.campusreserve.accounts;

import java.util.Locale;

/**
 * What an account is allowed to do at the coarsest level. Two roles are enough
 * for this platform: most people hold their own records, and a small staff group
 * works on everyone's.
 */
public enum Role {

    /** An ordinary person. Acts on their own records only. */
    MEMBER("member"),

    /** Staff. Acts on any record and reaches the administrative operations. */
    ADMIN("admin");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    /**
     * The stable spelling used on the wire and in stored data. The enum constant
     * name can be renamed later; this string cannot.
     */
    public String code() {
        return code;
    }

    /** Does this role reach the administrative operations? */
    public boolean canAdminister() {
        return this == ADMIN;
    }

    /**
     * The role for a wire code. Case and surrounding whitespace are ignored so
     * that a header or a stored field does not have to be cleaned up first.
     */
    public static Role fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role code is required");
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Role role : values()) {
            if (role.code.equals(wanted)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role code: " + value);
    }
}
