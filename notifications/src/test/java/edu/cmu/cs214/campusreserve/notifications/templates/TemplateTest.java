package edu.cmu.cs214.campusreserve.notifications.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemplateTest {

    private static final Map<String, String> VALUES = Map.of(
        Placeholders.NAME, "Dana",
        Placeholders.ROOM_ID, "r-401",
        Placeholders.WHEN, "2026-10-20 10:00 to 11:00 UTC",
        Placeholders.ATTENDEES, "4",
        Placeholders.BOOKING_ID, "b-1001");

    @Nested
    class Engine {

        @Test
        void fillsEveryPlaceholderIncludingRepeatedOnes() {
            assertEquals("Room r-401 at 10:00", Templates.render(
                "Room {roomId} at {when}", Map.of("roomId", "r-401", "when", "10:00")));
            assertEquals("x then x", Templates.render("{a} then {a}", Map.of("a", "x")));
            assertEquals("nothing to fill", Templates.render("nothing to fill", Map.of()));
        }

        @Test
        void refusesAPlaceholderWithNoValue() {
            TemplateException failure = assertThrows(TemplateException.class,
                () -> Templates.render("Room {roomId} at {when}", Map.of("roomId", "r-401")));
            assertTrue(failure.getMessage().contains("when"), failure.getMessage());
        }

        @Test
        void refusesAValueThatMatchesNoPlaceholder() {
            TemplateException failure = assertThrows(TemplateException.class,
                () -> Templates.render("Room {roomId}", Map.of("roomId", "r-401", "extra", "x")));
            assertTrue(failure.getMessage().contains("extra"), failure.getMessage());
        }

        @Test
        void refusesBrokenPlaceholders() {
            assertThrows(TemplateException.class, () -> Templates.render("Room {roomId", Map.of()));
            assertThrows(TemplateException.class, () -> Templates.render("Room {}", Map.of()));
            assertThrows(TemplateException.class, () -> Templates.render("Room {room-id}", Map.of()));
        }

    }

    @Nested
    class Shipped {

        @Test
        void theConfirmationNamesTheRoomTheTimeAndTheBookingNumber() {
            MessageTemplate template = new BookingConfirmedTemplate();

            assertEquals(NotificationKind.BOOKING_CONFIRMED, template.kind());
            assertEquals("Room r-401 is booked for 2026-10-20 10:00 to 11:00 UTC",
                template.subject(VALUES));

            String body = template.body(VALUES);
            assertTrue(body.startsWith("Hi Dana,"), body);
            assertTrue(body.contains("4 people"), body);
            assertTrue(body.contains("b-1001"), body);
        }

        @Test
        void eachTemplateAsksForTheValuesItsWordingUses() {
            assertEquals(Set.of(Placeholders.NAME, Placeholders.ROOM_ID, Placeholders.WHEN,
                Placeholders.BOOKING_ID), new BookingCancelledTemplate().placeholders());
            assertTrue(new WaitlistPromotedTemplate().placeholders().contains(Placeholders.POSITION));
        }

        @Test
        void aValueMapThatDoesNotMatchTheWordingFails() {
            MessageTemplate template = new BookingConfirmedTemplate();
            Map<String, String> missing = new HashMap<>(VALUES);
            missing.remove(Placeholders.ATTENDEES);
            Map<String, String> extra = new HashMap<>(VALUES);
            extra.put("floor", "4");

            assertTrue(assertThrows(TemplateException.class, () -> template.subject(missing))
                .getMessage().contains(Placeholders.ATTENDEES));
            assertThrows(TemplateException.class, () -> template.body(extra));
        }
    }

    @Nested
    class Registry {

        @Test
        void theStandardRegistryCoversEveryKind() {
            TemplateRegistry registry = TemplateRegistry.standard();
            assertTrue(registry.isComplete());
            assertEquals(NotificationKind.values().length, registry.kinds().size());
        }

        @Test
        void aMissingTemplateNamesTheKindItIsMissingFor() {
            TemplateRegistry registry = TemplateRegistry.empty();

            assertFalse(registry.isComplete());
            assertTrue(registry.find(NotificationKind.BOOKING_CONFIRMED).isEmpty());
            assertTrue(assertThrows(TemplateException.class,
                () -> registry.require(NotificationKind.BOOKING_CANCELLED))
                .getMessage().contains("booking-cancelled"));
        }

        @Test
        void registeringASecondTemplateForAKindReplacesTheFirst() {
            MessageTemplate replacement = new PlainConfirmation();
            TemplateRegistry registry = TemplateRegistry.standard().register(replacement);

            assertSame(replacement, registry.require(NotificationKind.BOOKING_CONFIRMED));
            assertEquals("Booked r-401", replacement.subject(Map.of("roomId", "r-401")));
        }
    }

    /** Stand-in wording, used to prove that registering replaces. */
    private static final class PlainConfirmation extends TextMessageTemplate {
        PlainConfirmation() {
            super(NotificationKind.BOOKING_CONFIRMED, "Booked {roomId}", "Room {roomId} is yours.");
        }
    }
}
