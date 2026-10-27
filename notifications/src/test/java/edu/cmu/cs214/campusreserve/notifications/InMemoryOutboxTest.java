package edu.cmu.cs214.campusreserve.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryOutboxTest {

    private final InMemoryOutbox outbox = new InMemoryOutbox();

    @BeforeEach
    void fill() {
        outbox.enqueue(Fixtures.notification("ntf-1"));
        outbox.enqueue(new Notification("ntf-2", "u-sam", "sam@example.edu", Channel.EMAIL,
            NotificationKind.BOOKING_CANCELLED, "subject", "body", Instant.EPOCH));
        outbox.enqueue(Fixtures.notification("ntf-3"));
    }

    @Test
    void keepsMessagesInTheOrderTheyArrivedAndPeekingLeavesThemThere() {
        assertEquals(List.of("ntf-1", "ntf-2", "ntf-3"), ids(outbox.pending()));
        assertEquals("ntf-1", outbox.peek().orElseThrow().id());
        assertEquals(3, outbox.size());
    }

    @Test
    void drainingReturnsEverythingInOrderAndEmptiesTheQueue() {
        assertEquals(List.of("ntf-1", "ntf-2", "ntf-3"), ids(outbox.drain()));

        assertTrue(outbox.isEmpty());
        assertTrue(outbox.peek().isEmpty());
        assertEquals(List.of(), outbox.drain());
    }

    @Test
    void asksPerRecipient() {
        assertEquals(List.of("ntf-1", "ntf-3"), ids(outbox.pendingFor(Fixtures.USER_ID)));
        assertEquals(List.of(), outbox.pendingFor("u-nobody"));
        assertEquals(List.of(), outbox.pendingFor(null));
    }

    private static List<String> ids(List<Notification> notifications) {
        return notifications.stream().map(Notification::id).toList();
    }
}
