package edu.cmu.cs214.campusreserve.core.support;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import edu.cmu.cs214.campusreserve.core.ports.EventPublisher;
import java.util.ArrayList;
import java.util.List;

/** Keeps every published event so tests can assert on the trail. */
public final class RecordingPublisher implements EventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
        if (failure != null) {
            throw failure;
        }
    }

    /** Makes every later publish throw, to check that booking flows survive it. */
    public void failWith(RuntimeException toThrow) {
        this.failure = toThrow;
    }

    /** Everything published so far, in order. */
    public List<DomainEvent> published() {
        return List.copyOf(published);
    }

    /** The wire names of everything published so far, in order. */
    public List<String> types() {
        return published.stream().map(DomainEvent::type).toList();
    }

    /** Everything published so far of one class. */
    public <T extends DomainEvent> List<T> ofType(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** How many events were published. */
    public int size() {
        return published.size();
    }
}
