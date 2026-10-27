package edu.cmu.cs214.campusreserve.storage;

import java.util.Objects;

/**
 * Builds one JSON object on one line.
 *
 * <p>The platform writes JSON and never reads it, so this is a writer and only a
 * writer. Fields come out in the order they are added, which keeps the event log
 * readable and keeps the test fixtures stable.
 */
public final class JsonLine {

    private final StringBuilder out = new StringBuilder("{");
    private boolean empty = true;

    /** Starts a new line. */
    public static JsonLine object() {
        return new JsonLine();
    }

    /** Adds a string field. A null value is written as JSON null. */
    public JsonLine field(String name, String value) {
        separate();
        writeKey(name);
        if (value == null) {
            out.append("null");
        } else {
            writeString(value);
        }
        return this;
    }

    /** Adds a whole-number field. */
    public JsonLine field(String name, long value) {
        separate();
        writeKey(name);
        out.append(value);
        return this;
    }

    /** Adds a boolean field. */
    public JsonLine field(String name, boolean value) {
        separate();
        writeKey(name);
        out.append(value);
        return this;
    }

    /** Adds a string field only when the value is present. */
    public JsonLine optionalField(String name, String value) {
        return value == null ? this : field(name, value);
    }

    /** Finishes the object and returns the line, with no trailing newline. */
    public String build() {
        return out + "}";
    }

    @Override
    public String toString() {
        return build();
    }

    /**
     * Escapes a string for JSON: quotes, backslashes, the named control escapes,
     * and a numeric escape for anything else below space.
     */
    public static String escape(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private void separate() {
        if (empty) {
            empty = false;
        } else {
            out.append(',');
        }
    }

    private void writeKey(String name) {
        writeString(Objects.requireNonNull(name, "name"));
        out.append(':');
    }

    private void writeString(String value) {
        out.append('"').append(escape(value)).append('"');
    }
}
