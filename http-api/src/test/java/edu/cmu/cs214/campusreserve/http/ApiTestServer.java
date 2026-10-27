package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.accounts.Role;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A running platform with a seeded catalog and a real HTTP server, plus the
 * client calls the endpoint tests make.
 *
 * <p>The server listens on a port the operating system picks, so the suite can
 * run anywhere and in parallel with anything.
 */
final class ApiTestServer implements AutoCloseable {

    static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");
    static final String DAY = "2026-10-20";

    private final CampusReservePlatform platform;
    private final HttpApiServer server;
    private final HttpClient client;

    private ApiTestServer() {
        this.platform = CampusReservePlatform.create(Clock.fixed(NOW, ZoneOffset.UTC));
        seed();
        this.server = platform.serveOn(0);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Starts a seeded platform on a free port. */
    static ApiTestServer start() {
        return new ApiTestServer();
    }

    /** The platform behind the server, for assertions about stored state. */
    CampusReservePlatform platform() {
        return platform;
    }

    /** A bearer token for one of the seeded accounts. */
    String tokenFor(String userId) {
        return platform.tokens().issue(userId, Duration.ofHours(1)).value();
    }

    /** An interval on the shared test day, as query parameters. */
    static String slot(String startTime, int minutes) {
        return "start=" + DAY + "T" + startTime + ":00Z&minutes=" + minutes;
    }

    /** A GET with no credentials. */
    HttpResponse<String> get(String path) {
        return send(request(path).GET().build());
    }

    /** A GET carrying a bearer token. */
    HttpResponse<String> get(String path, String token) {
        return send(authorized(request(path), token).GET().build());
    }

    /** A form POST carrying a bearer token. */
    HttpResponse<String> post(String path, String form, String token) {
        HttpRequest.Builder builder = authorized(request(path), token)
            .header("Content-Type", FormValues.FORM_CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(form));
        return send(builder.build());
    }

    /** A form POST with no credentials. */
    HttpResponse<String> postWithoutToken(String path, String form) {
        return send(request(path)
            .header("Content-Type", FormValues.FORM_CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build());
    }

    /** A DELETE carrying a bearer token. */
    HttpResponse<String> delete(String path, String token) {
        return send(authorized(request(path), token).DELETE().build());
    }

    /** A request with a method the API does not serve on that path. */
    HttpResponse<String> put(String path, String token) {
        return method("PUT", path, "", token);
    }

    /**
     * Any method with a form body and a bearer token. Use this for a method the
     * helpers above do not cover.
     */
    HttpResponse<String> method(String httpMethod, String path, String form, String token) {
        HttpRequest.Builder builder = authorized(request(path), token)
            .method(httpMethod, HttpRequest.BodyPublishers.ofString(form));
        if (!form.isEmpty()) {
            builder.header("Content-Type", FormValues.FORM_CONTENT_TYPE);
        }
        return send(builder.build());
    }

    @Override
    public void close() {
        server.stop();
        platform.close();
    }

    private void seed() {
        platform.accounts().register(
            new Account("u-1", "Ada", "ada@andrew.cmu.edu", Role.MEMBER, true, NOW));
        platform.accounts().register(
            new Account("u-2", "Blake", "blake@andrew.cmu.edu", Role.MEMBER, true, NOW));
        platform.accounts().register(
            new Account("u-3", "Cass", "cass@andrew.cmu.edu", Role.ADMIN, true, NOW));
        platform.syncAccountsIntoBookingDomain();

        platform.rooms().register(Room.active("r-1", "Study 205", "Sorrells", 8, RoomKind.STUDY_ROOM));
        platform.rooms().register(Room.active("r-2", "Lecture 1", "Wean", 60, RoomKind.CLASSROOM));
        platform.rooms().register(
            new Room("r-3", "Bench Lab", "Hamerschlag", 12, RoomKind.LAB, false));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
            .timeout(Duration.ofSeconds(5));
    }

    private static HttpRequest.Builder authorized(HttpRequest.Builder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("The request failed: " + request.uri(), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The request was interrupted", interrupted);
        }
    }
}
