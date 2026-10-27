package edu.cmu.cs214.campusreserve.notifications.delivery;

/**
 * Delivery did not happen.
 *
 * <p>The {@code permanent} flag is the transport's own read on whether trying
 * again could help. A gateway that is briefly unreachable is temporary. An
 * address the gateway rejects outright is permanent, and retrying it only
 * delays the rest of the queue.
 */
public class TransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean permanent;

    /** A failure that could clear up on its own. */
    public TransportException(String message) {
        this(message, false);
    }

    /** A failure, saying whether trying again could help. */
    public TransportException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    /** Would another attempt fail the same way? */
    public boolean isPermanent() {
        return permanent;
    }
}
