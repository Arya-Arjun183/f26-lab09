package edu.cmu.cs214.campusreserve.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Matches a method and a path against the registered routes.
 *
 * <p>Patterns are slash-separated segments. A segment written {@code {name}}
 * matches one segment and becomes a path parameter. Routes are tried in the
 * order they were added, so register the more specific pattern first.
 *
 * <p>The router tells a missing path apart from a wrong method: a path that
 * matches some route under a different method yields
 * {@link Status#METHOD_ALLOWED_ELSEWHERE}, which the server turns into a 405.
 */
public final class Router {

    private final List<Route> routes = new ArrayList<>();

    /** One registered route. */
    public record Route(String method, String pattern, ApiHandler handler) {
        public Route {
            method = Objects.requireNonNull(method, "method").toUpperCase();
            pattern = normalize(Objects.requireNonNull(pattern, "pattern"));
            Objects.requireNonNull(handler, "handler");
        }
    }

    /** A route that matched, plus the path parameters it captured. */
    public record Match(Route route, Map<String, String> pathParams) {
        public Match {
            pathParams = Map.copyOf(pathParams);
        }
    }

    /** What happened when the router looked at a request. */
    public sealed interface Lookup {
        /** A route matched. */
        record Found(Match match) implements Lookup {
        }

        /** The path matches a route registered under other methods. */
        record WrongMethod(List<String> allowed) implements Lookup {
            public WrongMethod {
                allowed = List.copyOf(allowed);
            }
        }

        /** No route has that path. */
        record NoRoute() implements Lookup {
        }
    }

    /** Registers a route. Returns this router so registrations can be chained. */
    public Router add(String method, String pattern, ApiHandler handler) {
        routes.add(new Route(method, pattern, handler));
        return this;
    }

    /** Registers a GET route. */
    public Router get(String pattern, ApiHandler handler) {
        return add("GET", pattern, handler);
    }

    /** Registers a POST route. */
    public Router post(String pattern, ApiHandler handler) {
        return add("POST", pattern, handler);
    }

    /** Registers a DELETE route. */
    public Router delete(String pattern, ApiHandler handler) {
        return add("DELETE", pattern, handler);
    }

    /** How many routes are registered. */
    public int size() {
        return routes.size();
    }

    /** Every registered route, in registration order. */
    public List<Route> routes() {
        return List.copyOf(routes);
    }

    /** Looks a request up. */
    public Lookup lookup(String method, String path) {
        Objects.requireNonNull(method, "method");
        String[] parts = split(normalize(Objects.requireNonNull(path, "path")));
        String wanted = method.toUpperCase();

        List<String> otherMethods = new ArrayList<>();
        for (Route route : routes) {
            Optional<Map<String, String>> captured = capture(route.pattern(), parts);
            if (captured.isEmpty()) {
                continue;
            }
            if (route.method().equals(wanted)) {
                return new Lookup.Found(new Match(route, captured.get()));
            }
            if (!otherMethods.contains(route.method())) {
                otherMethods.add(route.method());
            }
        }
        return otherMethods.isEmpty() ? new Lookup.NoRoute() : new Lookup.WrongMethod(otherMethods);
    }

    private static Optional<Map<String, String>> capture(String pattern, String[] pathParts) {
        String[] patternParts = split(pattern);
        if (patternParts.length != pathParts.length) {
            return Optional.empty();
        }
        Map<String, String> captured = new LinkedHashMap<>();
        for (int i = 0; i < patternParts.length; i++) {
            String expected = patternParts[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                String name = expected.substring(1, expected.length() - 1);
                if (pathParts[i].isEmpty()) {
                    return Optional.empty();
                }
                captured.put(name, pathParts[i]);
            } else if (!expected.equals(pathParts[i])) {
                return Optional.empty();
            }
        }
        return Optional.of(captured);
    }

    private static String[] split(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }

    private static String normalize(String path) {
        String result = path.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
