package edu.cmu.cs214.campusreserve.notifications;

import java.util.List;
import java.util.Optional;

/**
 * Where rendered messages wait for a transport.
 *
 * <p>The outbox is the seam between writing a message and delivering it. The
 * booking flow finishes as soon as the message is queued, so a slow or broken
 * transport cannot hold a booking up.
 *
 * <p>Order is first in, first out.
 */
public interface Outbox {

    /** Adds a message to the back of the queue. */
    void enqueue(Notification notification);

    /** How many messages are waiting. */
    int size();

    /** The message at the front, left in place, or empty if there is none. */
    Optional<Notification> peek();

    /** Everything waiting, in queue order. The queue is left alone. */
    List<Notification> pending();

    /** Everything waiting for one user, in queue order. The queue is left alone. */
    List<Notification> pendingFor(String userId);

    /** Takes everything out, in queue order, and leaves the queue empty. */
    List<Notification> drain();

    /** Is the queue empty? */
    default boolean isEmpty() {
        return size() == 0;
    }
}
