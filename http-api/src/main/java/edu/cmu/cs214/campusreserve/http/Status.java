package edu.cmu.cs214.campusreserve.http;

/** The HTTP status codes this API returns. */
public final class Status {

    /** The request succeeded. */
    public static final int OK = 200;

    /** A new record was stored. */
    public static final int CREATED = 201;

    /** The request was accepted but did not produce a record, for example a waitlisting. */
    public static final int ACCEPTED = 202;

    /** The request was malformed or broke a booking rule. */
    public static final int BAD_REQUEST = 400;

    /** No usable credentials were presented. */
    public static final int UNAUTHORIZED = 401;

    /** The caller is known but may not do this. */
    public static final int FORBIDDEN = 403;

    /** No such path, or no such record. */
    public static final int NOT_FOUND = 404;

    /** The path exists but not with that method. */
    public static final int METHOD_NOT_ALLOWED = 405;

    /** The request collides with the current state, for example a taken slot. */
    public static final int CONFLICT = 409;

    /** Something went wrong that the caller cannot fix. */
    public static final int INTERNAL_ERROR = 500;

    private Status() {
    }

    /** Does this status mean the request succeeded? */
    public static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }
}
