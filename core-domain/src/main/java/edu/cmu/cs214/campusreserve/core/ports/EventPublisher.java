package edu.cmu.cs214.campusreserve.core.ports;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;

/**
 * Where domain events go once the service has committed a change.
 *
 * <p>Publishing is fire and forget: a subscriber that throws must not roll back
 * the booking that already happened, so implementations swallow their own
 * failures rather than letting them escape into the booking flow.
 */
@FunctionalInterface
public interface EventPublisher {

    /** Hands the event to whoever is listening. */
    void publish(DomainEvent event);

    /** A publisher that drops everything, for callers that want no event trail. */
    static EventPublisher noop() {
        return event -> {
        };
    }
}
