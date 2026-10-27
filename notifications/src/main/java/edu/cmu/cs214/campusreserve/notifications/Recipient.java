package edu.cmu.cs214.campusreserve.notifications;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import edu.cmu.cs214.campusreserve.core.domain.User;
import java.util.Objects;

/**
 * Who a message goes to.
 *
 * <p>This is a copy of the contact details, not a link to whatever system
 * owns them. A notification already queued keeps the address it was addressed
 * to even if the person later changes it.
 */
public record Recipient(String userId, String name, String email) {

    public Recipient {
        userId = Identifiers.require(userId, "user id");
        name = Identifiers.requireText(name, "recipient name");
        email = requireAddress(email);
    }

    /** Copies the contact details out of a domain user. */
    public static Recipient of(User user) {
        Objects.requireNonNull(user, "user");
        return new Recipient(user.id(), user.name(), user.email());
    }

    /** The first word of the name, which is what greetings use. */
    public String greetingName() {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.substring(0, space);
    }

    private static String requireAddress(String value) {
        String trimmed = Identifiers.requireText(value, "recipient address");
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            throw new IllegalArgumentException("Address must contain one at sign: " + value);
        }
        return trimmed;
    }
}
