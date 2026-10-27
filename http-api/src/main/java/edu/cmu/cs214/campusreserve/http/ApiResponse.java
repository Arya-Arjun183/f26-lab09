package edu.cmu.cs214.campusreserve.http;

import java.util.Objects;

/**
 * What a handler hands back: a status, a body, and the content type to send it
 * with. Bodies are always JSON, built by hand.
 */
public record ApiResponse(int status, String body, String contentType) {

    /** The content type every response in this API uses. */
    public static final String JSON = "application/json; charset=utf-8";

    public ApiResponse {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(contentType, "contentType");
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Not an HTTP status: " + status);
        }
    }

    /** A JSON response with the given status. */
    public static ApiResponse json(int status, JsonObject body) {
        return new ApiResponse(status, body.render(), JSON);
    }

    /** A 200 carrying an object. */
    public static ApiResponse ok(JsonObject body) {
        return json(Status.OK, body);
    }

    /** A 200 carrying an array under the field {@code name}. */
    public static ApiResponse ok(String name, JsonArray items) {
        return json(Status.OK, JsonObject.create().field(name, items).field("count", items.size()));
    }

    /** A 201 carrying the record that was stored. */
    public static ApiResponse created(JsonObject body) {
        return json(Status.CREATED, body);
    }

    /** An error body: a stable code plus a sentence a person can read. */
    public static ApiResponse error(int status, String code, String message) {
        return json(status, JsonObject.create()
            .field("error", code)
            .field("message", message));
    }

    /** Did the request succeed? */
    public boolean isSuccess() {
        return Status.isSuccess(status);
    }
}
