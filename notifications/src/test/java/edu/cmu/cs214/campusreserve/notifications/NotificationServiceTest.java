package edu.cmu.cs214.campusreserve.notifications;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.ports.EventPublisher;
import edu.cmu.cs214.campusreserve.notifications.NotificationService.SkippedEvent;
import edu.cmu.cs214.campusreserve.notifications.delivery.DispatchReport;
import edu.cmu.cs214.campusreserve.notifications.delivery.Dispatcher;
import edu.cmu.cs214.campusreserve.notifications.delivery.RecordingTransport;
import edu.cmu.cs214.campusreserve.notifications.delivery.RetryPolicy;
import edu.cmu.cs214.campusreserve.notifications.support.FailingTransport;
import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import edu.cmu.cs214.campusreserve.notifications.support.TestClock;
import edu.cmu.cs214.campusreserve.notifications.templates.TemplateRegistry;
import edu.cmu.cs214.campusreserve.notifications.templates.TextMessageTemplate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final TestClock clock = TestClock.at("2026-10-19T09:00:00Z");
    private final NotificationService service =
        new NotificationService(Fixtures.directory(), outbox, clock);

    @Nested
    class Writing {

        @Test
        void aCreatedBookingProducesAConfirmation() {
            service.publish(Fixtures.created());

            Notification notification = outbox.peek().orElseThrow();
            assertEquals(NotificationKind.BOOKING_CONFIRMED, notification.kind());
            assertEquals(Channel.EMAIL, notification.channel());
            assertEquals(Fixtures.USER_ID, notification.recipientUserId());
            assertEquals("dana@example.edu", notification.recipientAddress());
            assertEquals("Room r-401 is booked for 2026-10-20 10:00 to 11:00 UTC",
                notification.subject());
            assertTrue(notification.body().contains("Hi Dana,"), notification.body());
            assertTrue(notification.body().contains("b-1001"), notification.body());
            assertEquals(1L, service.queuedCount());
            assertEquals(0, service.skippedCount());
        }

        @Test
        void aCancellationAndAPromotionGetTheirOwnWording() {
            service.publish(Fixtures.cancelled());
            service.publish(Fixtures.promoted());

            List<Notification> queued = outbox.pending();
            assertEquals("Booking for room r-401 is cancelled", queued.get(0).subject());
            assertTrue(queued.get(0).body().contains("2026-10-20 10:00 to 11:00 UTC"));
            assertEquals(NotificationKind.WAITLIST_PROMOTED, queued.get(1).kind());
            assertTrue(queued.get(1).body().contains("number 2 on the waiting list"),
                queued.get(1).body());
        }

        @Test
        void everyMessageIsStampedFromTheInjectedClockAndGetsItsOwnId() {
            service.publish(Fixtures.created());
            clock.advanceMinutes(30);
            service.publish(Fixtures.cancelled());

            List<Notification> queued = outbox.pending();
            assertEquals(Instant.parse("2026-10-19T09:00:00Z"), queued.get(0).createdAt());
            assertEquals(Instant.parse("2026-10-19T09:30:00Z"), queued.get(1).createdAt());
            assertNotEquals(queued.get(0).id(), queued.get(1).id());
        }

        @Test
        void anSmsServiceCutsBodiesToTheChannelLimit() {
            NotificationService sms = new NotificationService(
                Fixtures.directory(), outbox, TemplateRegistry.standard(), clock, Channel.SMS);

            sms.publish(Fixtures.created());

            Notification notification = outbox.peek().orElseThrow();
            assertEquals(Channel.SMS, notification.channel());
            assertTrue(notification.body().length() <= Channel.SMS.maxBodyCharacters());
        }
    }

    /**
     * Nothing that goes wrong while writing a message may escape into the
     * booking flow. Every case here has to be recorded rather than thrown.
     */
    @Nested
    class Failures {

        @Test
        void anUnknownRecipientIsRecordedRatherThanThrown() {
            NotificationService orphaned =
                new NotificationService(UserDirectory.empty(), outbox, clock);

            assertDoesNotThrow(() -> orphaned.publish(Fixtures.created()));

            assertEquals(0, outbox.size());
            assertEquals(1, orphaned.skippedCount());
            SkippedEvent skipped = orphaned.skipped().get(0);
            assertEquals("booking-created", skipped.eventType());
            assertEquals(Fixtures.BOOKING_ID, skipped.bookingId());
            assertEquals(Fixtures.USER_ID, skipped.userId());
            assertTrue(skipped.reason().contains("directory"), skipped.reason());
        }

        @Test
        void aMissingTemplateIsRecordedRatherThanThrown() {
            NotificationService bare = new NotificationService(
                Fixtures.directory(), outbox, TemplateRegistry.empty(), clock, Channel.EMAIL);

            assertDoesNotThrow(() -> bare.publish(Fixtures.created()));

            assertEquals(0, outbox.size());
            assertEquals(0L, bare.queuedCount());
            assertTrue(bare.skipped().get(0).reason().contains("booking-confirmed"));
        }

        @Test
        void aTemplateThatWantsValuesNobodySuppliesDoesNotStopTheOtherKinds() {
            NotificationService picky = new NotificationService(Fixtures.directory(), outbox,
                TemplateRegistry.standard().register(new DemandingTemplate()), clock, Channel.EMAIL);

            assertDoesNotThrow(() -> picky.publish(Fixtures.created()));
            picky.publish(Fixtures.cancelled());
            picky.publish(Fixtures.promoted());

            assertEquals(2, outbox.size());
            assertEquals(2L, picky.queuedCount());
            assertEquals(1, picky.skippedCount());
            assertTrue(picky.skipped().get(0).reason().contains("floor"));
        }
    }

    @Nested
    class Pipeline {

        @Test
        void everyEventEndsUpAsADeliveredMessage() {
            EventPublisher publisher = service;
            publisher.publish(Fixtures.created());
            publisher.publish(Fixtures.promoted());
            publisher.publish(Fixtures.cancelled());
            RecordingTransport transport = new RecordingTransport();

            DispatchReport report = Dispatcher.singleAttempt(transport).dispatch(outbox);

            assertEquals(3, report.delivered());
            assertTrue(report.allDelivered());
            assertEquals(3, transport.sentTo(Fixtures.USER_ID).size());
        }

        @Test
        void aTransportThatIsDownCostsMessagesAndNothingElse() {
            FailingTransport transport = new FailingTransport(false);
            service.publish(Fixtures.created());

            DispatchReport report = assertDoesNotThrow(
                () -> new Dispatcher(transport, RetryPolicy.transientFailures(2)).dispatch(outbox));

            assertEquals(1, report.failed());
            assertEquals(2, transport.calls());
            assertEquals(1L, service.queuedCount());
            assertEquals(0, service.skippedCount());
        }
    }

    /** Wording that asks for a value the service does not supply. */
    private static final class DemandingTemplate extends TextMessageTemplate {
        DemandingTemplate() {
            super(NotificationKind.BOOKING_CONFIRMED, "Room {roomId}", "Floor {floor}, room {roomId}.");
        }
    }
}
