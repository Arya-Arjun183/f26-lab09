package edu.cmu.cs214.campusreserve.notifications.support;

import edu.cmu.cs214.campusreserve.notifications.Notification;
import edu.cmu.cs214.campusreserve.notifications.delivery.Transport;
import edu.cmu.cs214.campusreserve.notifications.delivery.TransportException;

/** A transport that never delivers anything, so the retry paths can be exercised. */
public final class FailingTransport implements Transport {

    private final boolean permanent;
    private int calls;

    /** @param permanent whether the failure claims another attempt is pointless */
    public FailingTransport(boolean permanent) {
        this.permanent = permanent;
    }

    @Override
    public void send(Notification notification) {
        calls++;
        throw new TransportException("gateway refused " + notification.id(), permanent);
    }

    /** How many times the transport was called. */
    public int calls() {
        return calls;
    }
}
