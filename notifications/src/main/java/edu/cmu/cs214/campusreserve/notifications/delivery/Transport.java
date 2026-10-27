package edu.cmu.cs214.campusreserve.notifications.delivery;

import edu.cmu.cs214.campusreserve.notifications.Notification;

/**
 * The thing that actually hands a message to the outside world.
 *
 * <p>A transport either delivers the message or throws. There is no return
 * value to inspect: everything a caller needs to know about a failure belongs
 * in the exception.
 */
@FunctionalInterface
public interface Transport {

    /**
     * Delivers one message.
     *
     * @throws TransportException when delivery did not happen. A transport that
     *     knows the failure is temporary marks the exception as such so the
     *     retry policy can act on it.
     */
    void send(Notification notification);
}
