package edu.cmu.cs214.campusreserve.notifications;

import java.util.Objects;

/**
 * How a message reaches a person.
 *
 * <p>Both channels address the recipient by the single address the directory
 * holds. The SMS gateway accepts messages at an address of the same shape as an
 * email address, so nothing here needs a second contact field.
 *
 * <p>The codes are the wire form. Anything that stores or reads a notification
 * outside this module sees the code, never the enum constant name.
 */
public enum Channel {

    /** Full-length messages with a subject line. */
    EMAIL("email", 5_000),

    /** Short messages: the gateway drops anything past the character limit. */
    SMS("sms", 320);

    private final String code;
    private final int maxBodyCharacters;

    Channel(String code, int maxBodyCharacters) {
        this.code = code;
        this.maxBodyCharacters = maxBodyCharacters;
    }

    /** The stable wire name, for example {@code email}. */
    public String code() {
        return code;
    }

    /** The longest body this channel carries. */
    public int maxBodyCharacters() {
        return maxBodyCharacters;
    }

    /**
     * Shortens {@code body} to fit the channel. A body that is already short
     * enough comes back unchanged. A longer one is cut and ends in three dots,
     * so a truncated message is obvious to whoever reads it.
     */
    public String fit(String body) {
        Objects.requireNonNull(body, "body");
        if (body.length() <= maxBodyCharacters) {
            return body;
        }
        return body.substring(0, maxBodyCharacters - 3) + "...";
    }

    /** Looks a channel up by its wire code. */
    public static Channel fromCode(String code) {
        for (Channel channel : values()) {
            if (channel.code.equals(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown channel code: " + code);
    }
}
