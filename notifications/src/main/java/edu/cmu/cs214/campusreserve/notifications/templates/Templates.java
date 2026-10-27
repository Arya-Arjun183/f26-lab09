package edu.cmu.cs214.campusreserve.notifications.templates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The substitution engine behind every message.
 *
 * <p>A template is plain text with placeholders in braces, for example
 * {@code Room {roomId} starts at {when}}. A placeholder name is one or more
 * letters, digits, or underscores.
 *
 * <p>Rendering is strict in both directions. A placeholder with no value fails,
 * and a value whose name appears nowhere in the template fails. Both cases are
 * a caller mistake, and a silent one produces a message with a raw
 * {@code {name}} in it or quietly drops information the caller meant to send.
 */
public final class Templates {

    private Templates() {
    }

    /**
     * Fills a template in.
     *
     * @throws TemplateException if the text has a broken placeholder, if a
     *     placeholder has no value, or if {@code values} carries a name the
     *     template never uses
     */
    public static String render(String template, Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Parsed parsed = parse(template);

        Set<String> missing = new TreeSet<>();
        StringBuilder out = new StringBuilder(template.length() + 32);
        for (int i = 0; i < parsed.names.size(); i++) {
            out.append(parsed.literals.get(i));
            String name = parsed.names.get(i);
            String value = values.get(name);
            if (value == null) {
                missing.add(name);
            } else {
                out.append(value);
            }
        }
        out.append(parsed.literals.get(parsed.literals.size() - 1));

        if (!missing.isEmpty()) {
            throw new TemplateException("No value for placeholder(s) " + missing);
        }
        Set<String> unused = new TreeSet<>(values.keySet());
        unused.removeAll(parsed.names);
        if (!unused.isEmpty()) {
            throw new TemplateException("Template has no placeholder(s) named " + unused);
        }
        return out.toString();
    }

    /**
     * The placeholder names the text uses, in the order they first appear.
     *
     * @throws TemplateException if the text has a broken placeholder
     */
    public static Set<String> placeholdersIn(String template) {
        return new LinkedHashSet<>(parse(template).names);
    }

    /**
     * Keeps only the entries {@code template} actually asks for. Callers use
     * this to render one part of a message from a value map that covers the
     * whole message.
     */
    public static Map<String, String> narrow(String template, Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Set<String> wanted = placeholdersIn(template);
        Map<String, String> narrowed = new LinkedHashMap<>();
        for (String name : wanted) {
            String value = values.get(name);
            if (value != null) {
                narrowed.put(name, value);
            }
        }
        return narrowed;
    }

    private static Parsed parse(String template) {
        Objects.requireNonNull(template, "template");
        List<String> literals = new ArrayList<>();
        List<String> names = new ArrayList<>();
        StringBuilder literal = new StringBuilder();

        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '{') {
                literal.append(c);
                i++;
                continue;
            }
            int close = template.indexOf('}', i);
            if (close < 0) {
                throw new TemplateException("Placeholder is never closed at position " + i);
            }
            String name = template.substring(i + 1, close);
            requireName(name, i);
            literals.add(literal.toString());
            literal.setLength(0);
            names.add(name);
            i = close + 1;
        }
        literals.add(literal.toString());
        return new Parsed(literals, names);
    }

    private static void requireName(String name, int position) {
        if (name.isEmpty()) {
            throw new TemplateException("Placeholder at position " + position + " has no name");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                throw new TemplateException(
                    "Placeholder name '" + name + "' has a character that is not allowed: " + c);
            }
        }
    }

    /** Text split at the placeholders: n names sit between n+1 literal pieces. */
    private record Parsed(List<String> literals, List<String> names) {
    }
}
