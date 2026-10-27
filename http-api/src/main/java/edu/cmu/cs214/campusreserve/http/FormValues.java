package edu.cmu.cs214.campusreserve.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code a=1&b=2} out of a query string or a form body.
 *
 * <p>The API takes its input as parameters rather than as a JSON body, which is
 * why the platform can ship without a JSON parser. A repeated name keeps every
 * value, in the order they arrived.
 */
public final class FormValues {

    /** The content type this parser understands in a request body. */
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private FormValues() {
    }

    /** Parses an encoded parameter string. A null or empty input yields no values. */
    public static Map<String, List<String>> parse(String encoded) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return values;
        }
        String body = encoded.startsWith("?") ? encoded.substring(1) : encoded;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.computeIfAbsent(decode(name), key -> new ArrayList<>()).add(decode(value));
        }
        return values;
    }

    /** Merges two parameter maps, keeping the values from both under each name. */
    public static Map<String, List<String>> merge(
        Map<String, List<String>> first, Map<String, List<String>> second) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        first.forEach((name, values) -> merged.put(name, new ArrayList<>(values)));
        second.forEach((name, values) ->
            merged.computeIfAbsent(name, key -> new ArrayList<>()).addAll(values));
        return merged;
    }

    /** Percent-decodes one name or value, turning a plus sign back into a space. */
    public static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException | IllegalArgumentException failure) {
            throw ApiException.badRequest("Could not decode a parameter: " + raw);
        }
    }
}
