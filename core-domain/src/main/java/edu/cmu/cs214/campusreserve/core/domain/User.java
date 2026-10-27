package edu.cmu.cs214.campusreserve.core.domain;

/**
 * Someone who can hold a booking. The domain keeps only what booking rules
 * need: an id, a display name, and an address to notify.
 */
public record User(String id, String name, String email) {

    public User {
        id = Identifiers.require(id, "user id");
        name = Identifiers.requireText(name, "user name");
        email = requireEmail(email);
    }

    /** The part of the address before the at sign, used in message greetings. */
    public String localPart() {
        return email.substring(0, email.indexOf('@'));
    }

    private static String requireEmail(String value) {
        String trimmed = Identifiers.requireText(value, "email");
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            throw new IllegalArgumentException("Email must contain one at sign: " + value);
        }
        if (trimmed.indexOf('.', at) < 0) {
            throw new IllegalArgumentException("Email domain must contain a dot: " + value);
        }
        return trimmed;
    }
}
