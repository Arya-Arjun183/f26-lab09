package edu.cmu.cs214.campusreserve.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The small types messages are built out of: times, codes, contacts, records. */
class ValueTypesTest {

    @Test
    void writesIntervalsForAPersonInUtc() {
        assertEquals("2026-10-20 10:00 to 11:00 UTC", IntervalText.formatUtc(Fixtures.interval()));
        assertEquals("2026-10-20 23:00 to 2026-10-21 01:00 UTC",
            IntervalText.formatUtc(TimeInterval.parse("2026-10-20T23:00:00Z", "2026-10-21T01:00:00Z")));
        assertEquals("2026-10-20 23:00 to 2026-10-21 00:00 UTC",
            IntervalText.formatUtc(TimeInterval.parse("2026-10-20T23:00:00Z", "2026-10-21T00:00:00Z")));
    }

    @Test
    void wireCodesSurviveARoundTrip() {
        assertEquals("email", Channel.EMAIL.code());
        assertEquals("sms", Channel.SMS.code());
        for (Channel channel : Channel.values()) {
            assertEquals(channel, Channel.fromCode(channel.code()));
        }
        assertEquals("waitlist-promoted", NotificationKind.WAITLIST_PROMOTED.code());
        for (NotificationKind kind : NotificationKind.values()) {
            assertEquals(kind, NotificationKind.fromCode(kind.code()));
        }
        assertThrows(IllegalArgumentException.class, () -> Channel.fromCode("pigeon"));
        assertThrows(IllegalArgumentException.class, () -> NotificationKind.fromCode("booking-invented"));
    }

    @Test
    void eachEventAsksForItsOwnKindOfMessage() {
        assertEquals(NotificationKind.BOOKING_CONFIRMED, NotificationKind.forEvent(Fixtures.created()));
        assertEquals(NotificationKind.BOOKING_CANCELLED, NotificationKind.forEvent(Fixtures.cancelled()));
        assertEquals(NotificationKind.WAITLIST_PROMOTED, NotificationKind.forEvent(Fixtures.promoted()));
    }

    @Test
    void aChannelCutsBodiesItCannotCarry() {
        assertEquals("short", Channel.SMS.fit("short"));
        String fitted = Channel.SMS.fit("x".repeat(400));
        assertEquals(Channel.SMS.maxBodyCharacters(), fitted.length());
        assertTrue(fitted.endsWith("..."));
    }

    @Test
    void aMessageKnowsWhoItIsForAndHowToDescribeItself() {
        Notification notification = Fixtures.notification("ntf-1");
        assertTrue(notification.isFor(Fixtures.USER_ID));
        assertFalse(notification.isFor("u-someone-else"));
        assertEquals("booking-confirmed to dana@example.edu over email", notification.summary());
    }

    @Test
    void aMessageRefusesBlankTextAndABodyTheChannelCannotCarry() {
        assertThrows(IllegalArgumentException.class, () -> message("   ", "body", Channel.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> message("subject", "", Channel.EMAIL));
        assertThrows(IllegalArgumentException.class,
            () -> message("subject", "x".repeat(Channel.SMS.maxBodyCharacters() + 1), Channel.SMS));
    }

    @Test
    void theDirectoryFindsWhoItWasGivenAndNobodyElse() {
        InMemoryUserDirectory directory = Fixtures.directory();

        assertEquals(Fixtures.recipient(), directory.find(Fixtures.USER_ID).orElseThrow());
        assertTrue(directory.find("u-nobody").isEmpty());
        assertTrue(directory.find("").isEmpty());
        assertTrue(directory.find(null).isEmpty());
        assertTrue(UserDirectory.empty().find(Fixtures.USER_ID).isEmpty());
    }

    @Test
    void addingTheSameIdTwiceKeepsTheNewerAddress() {
        InMemoryUserDirectory directory = Fixtures.directory()
            .add(new Recipient(Fixtures.USER_ID, "Dana Reyes", "dana.reyes@example.edu"));

        assertEquals(1, directory.size());
        assertEquals("dana.reyes@example.edu", directory.find(Fixtures.USER_ID).orElseThrow().email());
        assertTrue(directory.remove(Fixtures.USER_ID));
        assertFalse(directory.remove(Fixtures.USER_ID));
        assertEquals(0, directory.size());
    }

    @Test
    void copiesContactDetailsOutOfDomainUsers() {
        InMemoryUserDirectory directory = InMemoryUserDirectory.of(
            List.of(new User("u-2", "Sam Okafor", "sam@example.edu")));

        assertEquals("Sam Okafor", directory.find("u-2").orElseThrow().name());
        assertEquals("Sam", directory.find("u-2").orElseThrow().greetingName());
        assertThrows(IllegalArgumentException.class,
            () -> new Recipient("u-3", "Ray Lin", "ray.example.edu"));
    }

    private static Notification message(String subject, String body, Channel channel) {
        return new Notification("ntf-1", Fixtures.USER_ID, "dana@example.edu", channel,
            NotificationKind.BOOKING_CONFIRMED, subject, body, Instant.EPOCH);
    }
}
