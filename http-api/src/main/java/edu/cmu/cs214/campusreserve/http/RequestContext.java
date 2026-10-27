package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a handler needs about one request: the method, the path, whatever
 * the route captured, the parameters, and the headers.
 *
 * <p>Parameters from the query string and from a form body share one namespace.
 * A handler asks for a name and does not care which side it arrived on.
 */
public final class RequestContext {

    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> params;
    private final Map<String, String> headers;

    public RequestContext(
        String method,
        String path,
        Map<String, String> pathParams,
        Map<String, List<String>> params,
        Map<String, String> headers) {
        this.method = Objects.requireNonNull(method, "method").toUpperCase();
        this.path = Objects.requireNonNull(path, "path");
        this.pathParams = Map.copyOf(pathParams);
        this.params = Map.copyOf(params);
        this.headers = lowerCaseKeys(headers);
    }

    /** A context with no parameters and no headers, for the simplest handlers. */
    public static RequestContext of(String method, String path) {
        return new RequestContext(method, path, Map.of(), Map.of(), Map.of());
    }

    /** The HTTP method, upper case. */
    public String method() {
        return method;
    }

    /** The request path, with no query string. */
    public String path() {
        return path;
    }

    /** The value the route captured for a path segment, for example {@code bookingId}. */
    public String pathParam(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalStateException("No path parameter named " + name + " on " + path);
        }
        return value;
    }

    /** The first value for a parameter, if the caller sent one. */
    public Optional<String> param(String name) {
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        String first = values.get(0).trim();
        return first.isEmpty() ? Optional.empty() : Optional.of(first);
    }

    /** Every value for a parameter, in the order they arrived. */
    public List<String> paramValues(String name) {
        return params.getOrDefault(name, List.of());
    }

    /** The parameter, or a 400 naming it. */
    public String requireParam(String name) {
        return param(name).orElseThrow(() -> ApiException.missingParameter(name));
    }

    /** The parameter as a whole number, or a 400. */
    public int requireInt(String name) {
        return parseInt(name, requireParam(name));
    }

    /** The parameter as a whole number, or the default when it is absent. */
    public int intParam(String name, int fallback) {
        return param(name).map(value -> parseInt(name, value)).orElse(fallback);
    }

    /** The parameter as an ISO-8601 instant, or a 400. */
    public Instant requireInstant(String name) {
        String value = requireParam(name);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException failure) {
            throw ApiException.badRequest(
                name + " must be an ISO-8601 instant such as 2026-10-20T10:00:00Z, got " + value);
        }
    }

    /** The parameter as a plain UTC date, or a 400. */
    public LocalDate requireDate(String name) {
        String value = requireParam(name);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException failure) {
            throw ApiException.badRequest(name + " must be a YYYY-MM-DD date, got " + value);
        }
    }

    /**
     * The booked interval, given either {@code start} and {@code end}, or
     * {@code start} and {@code minutes}. Anything the domain refuses comes back
     * as a 400 rather than as a stack trace.
     */
    public TimeInterval requireInterval() {
        Instant start = requireInstant("start");
        try {
            if (param("end").isPresent()) {
                return TimeInterval.of(start, requireInstant("end"));
            }
            return TimeInterval.ofMinutes(start, requireInt("minutes"));
        } catch (IllegalArgumentException failure) {
            throw ApiException.badRequest(failure.getMessage());
        }
    }

    /** A header value, looked up without regard to case. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase()));
    }

    /** The raw {@code Authorization} header, if the caller sent one. */
    public Optional<String> authorizationHeader() {
        return header("Authorization");
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw ApiException.badRequest(name + " must be a whole number, got " + value);
        }
    }

    private static Map<String, String> lowerCaseKeys(Map<String, String> input) {
        Map<String, String> lowered = new java.util.LinkedHashMap<>();
        input.forEach((name, value) -> lowered.put(name.toLowerCase(), value));
        return Map.copyOf(lowered);
    }
}
