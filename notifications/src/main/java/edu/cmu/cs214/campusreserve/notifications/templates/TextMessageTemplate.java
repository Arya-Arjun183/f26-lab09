package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A template written as two pieces of text: one for the subject, one for the
 * body.
 *
 * <p>The value map is checked against the two pieces together before either is
 * rendered. That way a value the body needs is not reported as an unknown name
 * while the subject is being written, and a caller that forgets a value hears
 * about it once rather than twice.
 */
public abstract class TextMessageTemplate implements MessageTemplate {

    private final NotificationKind kind;
    private final String subjectText;
    private final String bodyText;
    private final Set<String> placeholders;

    /**
     * @param kind the kind of message this template writes
     * @param subjectText subject line text, with placeholders in braces
     * @param bodyText body text, with placeholders in braces
     */
    protected TextMessageTemplate(NotificationKind kind, String subjectText, String bodyText) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.subjectText = Objects.requireNonNull(subjectText, "subjectText");
        this.bodyText = Objects.requireNonNull(bodyText, "bodyText");
        Set<String> names = new LinkedHashSet<>(Templates.placeholdersIn(subjectText));
        names.addAll(Templates.placeholdersIn(bodyText));
        this.placeholders = Set.copyOf(names);
    }

    @Override
    public final NotificationKind kind() {
        return kind;
    }

    @Override
    public final Set<String> placeholders() {
        return placeholders;
    }

    @Override
    public final String subject(Map<String, String> values) {
        require(values);
        return Templates.render(subjectText, Templates.narrow(subjectText, values));
    }

    @Override
    public final String body(Map<String, String> values) {
        require(values);
        return Templates.render(bodyText, Templates.narrow(bodyText, values));
    }

    private void require(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Set<String> missing = new TreeSet<>(placeholders);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw new TemplateException(
                kind.code() + " template has no value for placeholder(s) " + missing);
        }
        Set<String> extra = new TreeSet<>(values.keySet());
        extra.removeAll(placeholders);
        if (!extra.isEmpty()) {
            throw new TemplateException(
                kind.code() + " template has no placeholder(s) named " + extra);
        }
    }
}
