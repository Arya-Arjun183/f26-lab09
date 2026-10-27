package edu.cmu.cs214.campusreserve.notifications;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An outbox held in memory.
 *
 * <p>Messages are lost if the process stops before they are drained. That is
 * the accepted trade for keeping the booking flow free of a write to durable
 * storage: a missed message is a smaller problem than a booking that fails
 * because the mail queue is down.
 *
 * <p>Every method is synchronized, so a dispatcher on one thread and booking
 * traffic on another do not interleave.
 */
public final class InMemoryOutbox implements Outbox {

    private final Deque<Notification> queue = new ArrayDeque<>();

    @Override
    public synchronized void enqueue(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        queue.addLast(notification);
    }

    @Override
    public synchronized int size() {
        return queue.size();
    }

    @Override
    public synchronized Optional<Notification> peek() {
        return Optional.ofNullable(queue.peekFirst());
    }

    @Override
    public synchronized List<Notification> pending() {
        return List.copyOf(queue);
    }

    @Override
    public synchronized List<Notification> pendingFor(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<Notification> matches = new ArrayList<>();
        for (Notification notification : queue) {
            if (notification.isFor(userId)) {
                matches.add(notification);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public synchronized List<Notification> drain() {
        List<Notification> drained = List.copyOf(queue);
        queue.clear();
        return drained;
    }
}
