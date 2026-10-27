package edu.cmu.cs214.campusreserve.notifications.delivery;

import edu.cmu.cs214.campusreserve.notifications.Notification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A transport that keeps every message instead of sending it anywhere.
 *
 * <p>It backs the development and demo setups, where the point is to read what
 * would have gone out. Messages accumulate until {@link #clear()}, so a long
 * run holds on to memory.
 */
public final class RecordingTransport implements Transport {

    private final List<Notification> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Notification notification) {
        sent.add(Objects.requireNonNull(notification, "notification"));
    }

    /** Everything handed over, in the order it arrived. */
    public List<Notification> sent() {
        return List.copyOf(new ArrayList<>(sent));
    }

    /** How many messages have been handed over. */
    public int count() {
        return sent.size();
    }

    /** The messages addressed to one user, in the order they arrived. */
    public List<Notification> sentTo(String userId) {
        List<Notification> matches = new ArrayList<>();
        for (Notification notification : sent) {
            if (notification.isFor(userId)) {
                matches.add(notification);
            }
        }
        return List.copyOf(matches);
    }

    /** Forgets everything handed over so far. */
    public void clear() {
        sent.clear();
    }
}
