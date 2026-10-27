package edu.cmu.cs214.campusreserve.notifications.delivery;

import edu.cmu.cs214.campusreserve.notifications.Notification;
import edu.cmu.cs214.campusreserve.notifications.Outbox;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Takes messages out of an outbox and hands them to a transport.
 *
 * <p>A run drains the whole queue. A message that fails every attempt allowed
 * by the policy is reported and dropped: it is not put back, because a message
 * that keeps failing would otherwise block the queue behind it forever and
 * nothing about delivery is worth blocking bookings over.
 *
 * <p>One failing message does not stop the run. The dispatcher moves on to the
 * next one and reports everything at the end.
 *
 * <p>This class has no thread of its own. Whoever wants dispatch to happen on a
 * schedule calls {@link #dispatch(Outbox)} on their own thread.
 */
public final class Dispatcher {

    private final Transport transport;
    private final RetryPolicy policy;

    /**
     * @param transport where messages go
     * @param policy how many times to try each one
     */
    public Dispatcher(Transport transport, RetryPolicy policy) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** A dispatcher that tries once and reports whatever failed. */
    public static Dispatcher singleAttempt(Transport transport) {
        return new Dispatcher(transport, RetryPolicy.none());
    }

    /** Drains the outbox and delivers everything in it. */
    public DispatchReport dispatch(Outbox outbox) {
        Objects.requireNonNull(outbox, "outbox");
        return dispatch(outbox.drain());
    }

    /** Delivers a batch of messages that is already in hand, in order. */
    public DispatchReport dispatch(List<Notification> notifications) {
        Objects.requireNonNull(notifications, "notifications");
        int delivered = 0;
        List<DeliveryFailure> failures = new ArrayList<>();
        for (Notification notification : notifications) {
            DeliveryFailure failure = deliver(notification);
            if (failure == null) {
                delivered++;
            } else {
                failures.add(failure);
            }
        }
        return new DispatchReport(delivered, failures);
    }

    /** Returns null when the message got through, otherwise what went wrong. */
    private DeliveryFailure deliver(Notification notification) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                transport.send(notification);
                return null;
            } catch (RuntimeException failure) {
                if (!policy.shouldRetry(attempts, failure)) {
                    return DeliveryFailure.of(notification, attempts, failure);
                }
            }
        }
    }
}
