package edu.cmu.cs214.campusreserve.notifications.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.notifications.InMemoryOutbox;
import edu.cmu.cs214.campusreserve.notifications.Notification;
import edu.cmu.cs214.campusreserve.notifications.support.FailingTransport;
import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeliveryTest {

    private static final TransportException TEMPORARY = new TransportException("busy");
    private static final TransportException PERMANENT = new TransportException("rejected", true);

    private final InMemoryOutbox outbox = new InMemoryOutbox();

    @Nested
    class Policy {

        @Test
        void oneAttemptMeansNoRetry() {
            assertEquals(1, RetryPolicy.none().maxAttempts());
            assertFalse(RetryPolicy.none().shouldRetry(1, TEMPORARY));
        }

        @Test
        void temporaryFailuresAreRetriedUpToTheLimitAndPermanentOnesNever() {
            RetryPolicy policy = RetryPolicy.transientFailures(3);

            assertTrue(policy.shouldRetry(1, TEMPORARY));
            assertTrue(policy.shouldRetry(2, TEMPORARY));
            assertFalse(policy.shouldRetry(3, TEMPORARY), "the limit is a total, not a retry count");
            assertFalse(policy.shouldRetry(1, PERMANENT));
            assertFalse(RetryPolicy.transientFailures(5).shouldRetry(1, PERMANENT));
        }

        @Test
        void refusesAnAttemptCountBelowOne() {
            assertThrows(IllegalArgumentException.class, () -> RetryPolicy.transientFailures(0));
            assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.none().shouldRetry(0, TEMPORARY));
        }
    }

    @Nested
    class Dispatch {

        @Test
        void deliversEverythingInTheOutboxAndLeavesItEmpty() {
            outbox.enqueue(Fixtures.notification("ntf-1"));
            outbox.enqueue(Fixtures.notification("ntf-2"));
            RecordingTransport transport = new RecordingTransport();

            DispatchReport report = Dispatcher.singleAttempt(transport).dispatch(outbox);

            assertEquals(2, report.delivered());
            assertEquals(0, report.failed());
            assertTrue(report.allDelivered());
            assertTrue(outbox.isEmpty());
            assertEquals(List.of("ntf-1", "ntf-2"), ids(transport.sent()));
            assertEquals(0, Dispatcher.singleAttempt(transport).dispatch(outbox).total());
        }

        @Test
        void aTemporaryFailureIsTriedAgainUntilItGoesThrough() {
            outbox.enqueue(Fixtures.notification("ntf-1"));
            int[] calls = {0};
            Transport busyTwice = notification -> {
                if (++calls[0] < 3) {
                    throw new TransportException("gateway busy");
                }
            };

            DispatchReport report =
                new Dispatcher(busyTwice, RetryPolicy.transientFailures(3)).dispatch(outbox);

            assertTrue(report.allDelivered());
            assertEquals(1, report.delivered());
            assertEquals(3, calls[0]);
        }

        @Test
        void aFailureThatOutlastsThePolicyIsReportedWithItsAttemptCount() {
            outbox.enqueue(Fixtures.notification("ntf-1"));
            FailingTransport transport = new FailingTransport(false);

            DispatchReport report =
                new Dispatcher(transport, RetryPolicy.transientFailures(3)).dispatch(outbox);

            assertEquals(0, report.delivered());
            assertEquals(1, report.failed());
            assertFalse(report.allDelivered());
            assertEquals(3, transport.calls());

            DeliveryFailure failure = report.failureFor("ntf-1").orElseThrow();
            assertEquals(3, failure.attempts());
            assertTrue(failure.wasRetried());
            assertEquals(Fixtures.USER_ID, failure.recipientUserId());
            assertTrue(failure.reason().contains("gateway refused"), failure.reason());
        }

        @Test
        void aPermanentFailureIsTriedOnceAndIsNotPutBack() {
            outbox.enqueue(Fixtures.notification("ntf-1"));
            FailingTransport transport = new FailingTransport(true);

            DispatchReport report =
                new Dispatcher(transport, RetryPolicy.transientFailures(5)).dispatch(outbox);

            assertEquals(1, transport.calls());
            assertEquals(1, report.failureFor("ntf-1").orElseThrow().attempts());
            assertFalse(report.failureFor("ntf-1").orElseThrow().wasRetried());
            assertTrue(outbox.isEmpty(), "a message that failed is not put back");
        }

        @Test
        void aFailureThatIsNotATransportFailureStopsThatMessageOnly() {
            Transport broken = notification -> {
                throw new IllegalStateException("transport is misconfigured");
            };
            outbox.enqueue(Fixtures.notification("ntf-1"));

            DeliveryFailure failure = new Dispatcher(broken, RetryPolicy.transientFailures(3))
                .dispatch(outbox).failureFor("ntf-1").orElseThrow();

            assertEquals(1, failure.attempts());
            assertEquals("transport is misconfigured", failure.reason());
        }

        @Test
        void oneFailingMessageDoesNotHoldUpTheRest() {
            RecordingTransport good = new RecordingTransport();
            Transport picky = notification -> {
                if (notification.id().equals("ntf-2")) {
                    throw new TransportException("address rejected", true);
                }
                good.send(notification);
            };
            outbox.enqueue(Fixtures.notification("ntf-1"));
            outbox.enqueue(Fixtures.notification("ntf-2"));
            outbox.enqueue(Fixtures.notification("ntf-3"));

            DispatchReport report = Dispatcher.singleAttempt(picky).dispatch(outbox);

            assertEquals(2, report.delivered());
            assertEquals(1, report.failed());
            assertEquals(3, report.total());
            assertEquals(List.of("ntf-1", "ntf-3"), ids(good.sent()));
        }
    }

    @Test
    void theRecordingTransportKeepsWhatItWasGivenUntilItIsCleared() {
        RecordingTransport transport = new RecordingTransport();
        transport.send(Fixtures.notification("ntf-1"));

        assertEquals(1, transport.count());
        assertEquals(1, transport.sentTo(Fixtures.USER_ID).size());
        assertTrue(transport.sentTo("u-nobody").isEmpty());
        transport.clear();
        assertEquals(0, transport.count());
    }

    private static List<String> ids(List<Notification> notifications) {
        return notifications.stream().map(Notification::id).toList();
    }
}
