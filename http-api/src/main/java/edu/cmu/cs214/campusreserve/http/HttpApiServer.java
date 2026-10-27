package edu.cmu.cs214.campusreserve.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The HTTP front door, built on the JDK's own server. There is no web framework
 * here and no dependency to add one.
 *
 * <p>The server does four things: read the request into a
 * {@link RequestContext}, ask the {@link Router} which handler serves it, run
 * the handler, and write what comes back. Every failure a handler raises comes
 * out as a JSON error body rather than as a stack trace on the wire.
 */
public final class HttpApiServer implements AutoCloseable {

    private final Router router;
    private final HttpServer server;

    public HttpApiServer(Router router, int port) {
        this.router = Objects.requireNonNull(router, "router");
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not open a server socket on port " + port, failure);
        }
        server.createContext("/", this::serve);
        server.setExecutor(null);
    }

    /** Starts listening. Port 0 asks the operating system to pick a free port. */
    public HttpApiServer start() {
        server.start();
        return this;
    }

    /** The port the server is actually listening on. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** The base address callers should use, for example {@code http://localhost:8080}. */
    public String baseUrl() {
        return "http://localhost:" + port();
    }

    /** Stops listening and lets in-flight requests finish. */
    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    /** Serves one exchange. Visible for the server's own use only. */
    private void serve(HttpExchange exchange) throws IOException {
        ApiResponse response;
        try {
            response = route(exchange);
        } catch (ApiException failure) {
            response = failure.toResponse();
        } catch (RuntimeException failure) {
            response = ApiResponse.error(Status.INTERNAL_ERROR, "internal-error",
                "The request could not be served: " + failure.getMessage());
        }
        write(exchange, response);
    }

    private ApiResponse route(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String method = exchange.getRequestMethod();
        String path = uri.getPath();

        Router.Lookup lookup = router.lookup(method, path);
        if (lookup instanceof Router.Lookup.NoRoute) {
            return ApiResponse.error(Status.NOT_FOUND, "no-such-endpoint", "No endpoint at " + path);
        }
        if (lookup instanceof Router.Lookup.WrongMethod wrongMethod) {
            return ApiResponse.error(Status.METHOD_NOT_ALLOWED, "method-not-allowed",
                path + " accepts " + String.join(", ", wrongMethod.allowed()) + ", not " + method);
        }

        Router.Match match = ((Router.Lookup.Found) lookup).match();
        Map<String, List<String>> params = FormValues.merge(
            FormValues.parse(uri.getRawQuery()), FormValues.parse(readFormBody(exchange)));
        RequestContext request = new RequestContext(
            method, path, match.pathParams(), params, headersOf(exchange));
        return match.route().handler().handle(request);
    }

    /**
     * Reads a form-encoded request body. Anything else is ignored: the API takes
     * its input as parameters, so there is nothing to parse.
     */
    private static String readFormBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && !contentType.toLowerCase().startsWith(FormValues.FORM_CONTENT_TYPE)) {
            return "";
        }
        try (var body = exchange.getRequestBody()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> headersOf(HttpExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<>();
        Headers incoming = exchange.getRequestHeaders();
        for (String name : incoming.keySet()) {
            String value = incoming.getFirst(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static void write(HttpExchange exchange, ApiResponse response) throws IOException {
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
