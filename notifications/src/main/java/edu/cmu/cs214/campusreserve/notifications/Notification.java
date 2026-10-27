package edu.cmu.cs214.campusreserve.notifications;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.time.Instant;
import java.util.Objects;

/**
 * One rendered message, ready to hand to a transport.
 *
 * <p>A notification is finished text. Nothing downstream re-renders it or looks
 * the recipient up again, so a message that sat in the outbox for an hour still
 * says what it said when it was written.
 *
 * @param id an id unique within the run that produced it
 * @param recipientUserId the user the message is about
 * @param recipientAddress the address the transport delivers to
 * @param channel how the message goes out
 * @param kind what the message is about
 * @param subject the subject line
 * @param body the message text
 * @param createdAt when the message was rendered
 */
public record Notification(
    String id,
    String recipientUserId,
    String recipientAddress,
    Channel channel,
    NotificationKind kind,
    String subject,
    String body,
    Instant createdAt) {

    public Notification {
        id = Identifiers.require(id, "notification id");
        recipientUserId = Identifiers.require(recipientUserId, "recipient user id");
        recipientAddress = Identifiers.require(recipientAddress, "recipient address");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        if (body.length() > channel.maxBodyCharacters()) {
            throw new IllegalArgumentException(
                "Body is longer than the " + channel.code() + " limit of " + channel.maxBodyCharacters());
        }
    }

    /** Is this message addressed to {@code candidateUserId}? */
    public boolean isFor(String candidateUserId) {
        return recipientUserId.equals(candidateUserId);
    }

    /** A one-line description for logs and failure reports. */
    public String summary() {
        return kind.code() + " to " + recipientAddress + " over " + channel.code();
    }
}
