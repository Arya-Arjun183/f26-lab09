package edu.cmu.cs214.campusreserve.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A JSON array being built for a response body. */
public final class JsonArray {

    private final List<String> elements = new ArrayList<>();

    JsonArray() {
    }

    /** Starts a new array. */
    public static JsonArray create() {
        return new JsonArray();
    }

    /** An array built by rendering each item with {@code toJson}. */
    public static <T> JsonArray of(Collection<T> items, Function<T, JsonObject> toJson) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(toJson, "toJson");
        JsonArray array = new JsonArray();
        for (T item : items) {
            array.add(toJson.apply(item));
        }
        return array;
    }

    /** An array of strings. */
    public static JsonArray ofStrings(Collection<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    /** Appends an object. */
    public JsonArray add(JsonObject value) {
        elements.add(Objects.requireNonNull(value, "value").render());
        return this;
    }

    /** Appends a string. */
    public JsonArray add(String value) {
        elements.add(JsonObject.quote(Objects.requireNonNull(value, "value")));
        return this;
    }

    /** Appends a whole number. */
    public JsonArray add(long value) {
        elements.add(Long.toString(value));
        return this;
    }

    /** How many elements the array holds. */
    public int size() {
        return elements.size();
    }

    /** Is the array empty? */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /** The array as one JSON string, with no trailing newline. */
    public String render() {
        return "[" + String.join(",", elements) + "]";
    }

    @Override
    public String toString() {
        return render();
    }
}
