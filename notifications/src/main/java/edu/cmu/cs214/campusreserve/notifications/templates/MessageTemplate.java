package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;
import java.util.Map;
import java.util.Set;

/**
 * The wording for one kind of message.
 *
 * <p>A template turns a map of values into a subject line and a body. It does
 * no lookups of its own: whoever calls it has already resolved the room, the
 * time, and the person.
 */
public interface MessageTemplate {

    /** The kind of message this template writes. */
    NotificationKind kind();

    /**
     * The value names a caller has to supply. Callers that build the value map
     * by hand can check it against this set before rendering.
     */
    Set<String> placeholders();

    /**
     * The subject line.
     *
     * @throws TemplateException if {@code values} does not match
     *     {@link #placeholders()} exactly
     */
    String subject(Map<String, String> values);

    /**
     * The message text.
     *
     * @throws TemplateException if {@code values} does not match
     *     {@link #placeholders()} exactly
     */
    String body(Map<String, String> values);
}
