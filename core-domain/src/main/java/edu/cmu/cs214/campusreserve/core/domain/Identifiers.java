package edu.cmu.cs214.campusreserve.core.domain;

/**
 * Shared validation for the small string fields the entities carry. Keeping the
 * checks here means every entity rejects blank identifiers the same way.
 */
public final class Identifiers {

    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 200;

    private Identifiers() {
    }

    /**
     * Validates an identifier: not blank, no whitespace, at most 64 characters.
     * Returns the trimmed value.
     */
    public static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(field + " is longer than " + MAX_ID_LENGTH);
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                throw new IllegalArgumentException(field + " must not contain whitespace");
            }
        }
        return trimmed;
    }

    /** Validates free text: not blank, at most 200 characters. Returns it trimmed. */
    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " is longer than " + MAX_TEXT_LENGTH);
        }
        return trimmed;
    }
}
