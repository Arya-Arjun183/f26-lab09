package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.storage.JsonLine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A JSON object being built for a response body.
 *
 * <p>The platform writes JSON and never reads it. String escaping comes from
 * {@link JsonLine#escape(String)} in the storage module so there is one escaping
 * implementation, and this class adds the nesting that response bodies need.
 *
 * <p>Fields keep the order they were added, which makes the responses readable
 * and the tests stable.
 */
public final class JsonObject {

    private final Map<String, String> fields = new LinkedHashMap<>();

    JsonObject() {
    }

    /** Starts a new object. */
    public static JsonObject create() {
        return new JsonObject();
    }

    /** Adds a string field. A null value is written as JSON null. */
    public JsonObject field(String name, String value) {
        return raw(name, value == null ? "null" : quote(value));
    }

    /** Adds a whole-number field. */
    public JsonObject field(String name, long value) {
        return raw(name, Long.toString(value));
    }

    /** Adds a boolean field. */
    public JsonObject field(String name, boolean value) {
        return raw(name, Boolean.toString(value));
    }

    /**
     * Adds a field holding a number with a fixed number of decimal places. Used
     * for rates and shares, which are the only fractional values on the wire.
     */
    public JsonObject field(String name, double value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("Decimal places must not be negative: " + decimals);
        }
        return raw(name, String.format("%." + decimals + "f", value));
    }

    /** Adds a nested object. */
    public JsonObject field(String name, JsonObject value) {
        return raw(name, Objects.requireNonNull(value, "value").render());
    }

    /** Adds a nested array. */
    public JsonObject field(String name, JsonArray value) {
        return raw(name, Objects.requireNonNull(value, "value").render());
    }

    /** Adds a string field only when the value is present. */
    public JsonObject optionalField(String name, String value) {
        return value == null ? this : field(name, value);
    }

    /** How many fields the object holds. */
    public int size() {
        return fields.size();
    }

    /** The object as one JSON string, with no trailing newline. */
    public String render() {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(entry.getKey())).append(':').append(entry.getValue());
        }
        return out.append('}').toString();
    }

    @Override
    public String toString() {
        return render();
    }

    static String quote(String value) {
        return "\"" + JsonLine.escape(value) + "\"";
    }

    private JsonObject raw(String name, String rendered) {
        fields.put(Objects.requireNonNull(name, "name"), rendered);
        return this;
    }
}
