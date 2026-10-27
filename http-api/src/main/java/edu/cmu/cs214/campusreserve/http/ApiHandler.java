package edu.cmu.cs214.campusreserve.http;

/**
 * Serves one request. A handler either returns a response or throws an
 * {@link ApiException}, which the server turns into the error body.
 */
@FunctionalInterface
public interface ApiHandler {

    /** Serves the request. */
    ApiResponse handle(RequestContext request);
}
