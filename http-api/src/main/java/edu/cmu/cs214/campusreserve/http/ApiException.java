package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import java.util.Objects;

/**
 * A request that cannot be served, carrying the status and the stable error code
 * to send back. Handlers throw this and the server turns it into a response, so
 * a handler does not have to thread error returns through its own helpers.
 */
public final class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.status = status;
        this.code = Objects.requireNonNull(code, "code");
    }

    /** A 400 for a parameter the caller got wrong. */
    public static ApiException badRequest(String message) {
        return new ApiException(Status.BAD_REQUEST, "bad-request", message);
    }

    /** A 400 for a parameter that is missing. */
    public static ApiException missingParameter(String name) {
        return new ApiException(Status.BAD_REQUEST, "missing-parameter", "Missing parameter: " + name);
    }

    /** A 401 for a caller who presented no usable credentials. */
    public static ApiException unauthorized(String message) {
        return new ApiException(Status.UNAUTHORIZED, "unauthorized", message);
    }

    /** A 403 for a caller who may not do this. */
    public static ApiException forbidden(String message) {
        return new ApiException(Status.FORBIDDEN, "forbidden", message);
    }

    /** A 404 for a record that is not there. */
    public static ApiException notFound(String message) {
        return new ApiException(Status.NOT_FOUND, "not-found", message);
    }

    /** The response carrying a domain rejection, with the reason's own code. */
    public static ApiException of(RejectionReason reason) {
        return new ApiException(ErrorMapping.statusFor(reason), reason.code(), reason.message());
    }

    /** The HTTP status to send. */
    public int status() {
        return status;
    }

    /** The stable machine-readable code to send. */
    public String code() {
        return code;
    }

    /** This failure as a response. */
    public ApiResponse toResponse() {
        return ApiResponse.error(status, code, getMessage());
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // These are expected outcomes on a request path, not faults worth a trace.
        return this;
    }
}
