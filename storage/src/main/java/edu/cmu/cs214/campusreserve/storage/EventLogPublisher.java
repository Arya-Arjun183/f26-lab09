package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import edu.cmu.cs214.campusreserve.core.ports.EventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends every domain event to the event log, then on to whoever else is
 * listening.
 *
 * <p>Subscribers are fire and forget. One that throws is recorded here and the
 * rest still run, because a notification that fails must not undo a booking that
 * already happened. The log write comes first so the trail is complete even when
 * a subscriber is broken.
 */
public final class EventLogPublisher implements EventPublisher {

    private final EventLogWriter log;
    private final List<EventPublisher> subscribers = new CopyOnWriteArrayList<>();
    private final List<SubscriberFailure> failures = new CopyOnWriteArrayList<>();

    public EventLogPublisher(EventLogWriter log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Adds a subscriber. Subscribers see events in the order they were added. */
    public EventLogPublisher subscribe(EventPublisher subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
        return this;
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        log.append(event);
        for (EventPublisher subscriber : subscribers) {
            try {
                subscriber.publish(event);
            } catch (RuntimeException failure) {
                failures.add(new SubscriberFailure(event.type(), event.bookingId(), failure.toString()));
            }
        }
    }

    /** The log this publisher writes to. */
    public EventLogWriter log() {
        return log;
    }

    /** How many subscribers are attached. */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** Subscriber failures seen so far, oldest first. */
    public List<SubscriberFailure> failures() {
        return List.copyOf(new ArrayList<>(failures));
    }

    /** One subscriber that threw, and the event it threw on. */
    public record SubscriberFailure(String eventType, String bookingId, String message) {
    }
}
