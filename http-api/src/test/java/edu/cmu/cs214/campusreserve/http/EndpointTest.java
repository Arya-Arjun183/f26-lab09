package edu.cmu.cs214.campusreserve.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The endpoints, exercised over a real socket. */
class EndpointTest {

    private ApiTestServer api;
    private String ada;
    private String blake;
    private String admin;

    @BeforeEach
    void setUp() {
        api = ApiTestServer.start();
        ada = api.tokenFor("u-1");
        blake = api.tokenFor("u-2");
        admin = api.tokenFor("u-3");
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    private String bookingIdFrom(String body) {
        int idAt = body.indexOf("\"id\":\"");
        assertTrue(idAt >= 0, "No booking id in " + body);
        int start = idAt + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private String bookAndGetId(String form, String token) {
        HttpResponse<String> response = api.post("/bookings", form, token);
        assertEquals(Status.CREATED, response.statusCode(), response.body());
        return bookingIdFrom(response.body());
    }

    @Nested
    @DisplayName("health and routing")
    class Plumbing {

        @Test
        void healthReportsWhatThePlatformHolds() {
            HttpResponse<String> response = api.get("/health");
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"ok\""));
            assertTrue(response.body().contains("\"rooms\":3"));
            assertTrue(response.body().contains("\"bookings\":0"));
        }

        @Test
        void anUnknownPathIsNotFound() {
            HttpResponse<String> response = api.get("/nowhere");
            assertEquals(Status.NOT_FOUND, response.statusCode());
            assertTrue(response.body().contains("no-such-endpoint"));
        }

        @Test
        void aMethodTheEndpointDoesNotServeIsRefused() {
            HttpResponse<String> response = api.put("/bookings", ada);
            assertEquals(Status.METHOD_NOT_ALLOWED, response.statusCode());
            assertTrue(response.body().contains("method-not-allowed"));
        }

        @Test
        void everyResponseIsJson() {
            assertEquals(ApiResponse.JSON,
                api.get("/health").headers().firstValue("Content-Type").orElseThrow());
        }
    }

    @Nested
    @DisplayName("rooms")
    class Rooms {

        @Test
        void listsActiveRoomsWithoutCredentials() {
            HttpResponse<String> response = api.get("/rooms");
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"count\":2"));
            assertTrue(response.body().contains("\"id\":\"r-1\""));
            assertFalse(response.body().contains("\"id\":\"r-3\""));
        }

        @Test
        void filtersByCapacityKindAndBuilding() {
            assertTrue(api.get("/rooms?minCapacity=20").body().contains("\"count\":1"));
            assertTrue(api.get("/rooms?kind=classroom").body().contains("\"id\":\"r-2\""));
            assertTrue(api.get("/rooms?building=Sorrells").body().contains("\"count\":1"));
        }

        @Test
        void refusesAFilterItCannotRead() {
            assertEquals(Status.BAD_REQUEST, api.get("/rooms?kind=broom-closet").statusCode());
            assertEquals(Status.BAD_REQUEST, api.get("/rooms?minCapacity=0").statusCode());
            assertEquals(Status.BAD_REQUEST, api.get("/rooms?minCapacity=many").statusCode());
        }

        @Test
        void showsOneRoomIncludingAnInactiveOne() {
            HttpResponse<String> response = api.get("/rooms/r-3");
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"active\":false"));
            assertTrue(response.body().contains("\"kindLabel\":\"Lab\""));
        }

        @Test
        void anUnknownRoomIsNotFound() {
            assertEquals(Status.NOT_FOUND, api.get("/rooms/r-missing").statusCode());
        }

        @Test
        void reportsFreeTimeOnADate() {
            HttpResponse<String> response = api.get("/rooms/r-1/availability?date=" + ApiTestServer.DAY);
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"start\":\"2026-10-20T06:00:00Z\""));
            assertTrue(response.body().contains("\"bookedMinutes\":0"));
            assertTrue(response.body().contains("\"utilization\":0.0000"));
            assertFalse(response.body().contains("\"slots\""));
        }

        @Test
        void reportsBookableSlotsWhenAskedForALength() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            HttpResponse<String> response =
                api.get("/rooms/r-1/availability?date=" + ApiTestServer.DAY + "&minutes=60");
            assertTrue(response.body().contains("\"slots\""));
            assertTrue(response.body().contains("\"bookedMinutes\":60"));
        }

        @Test
        void availabilityNeedsAReadableDate() {
            assertEquals(Status.BAD_REQUEST, api.get("/rooms/r-1/availability?date=soon").statusCode());
            assertEquals(Status.BAD_REQUEST, api.get("/rooms/r-1/availability").statusCode());
            assertEquals(Status.BAD_REQUEST,
                api.get("/rooms/r-1/availability?date=" + ApiTestServer.DAY + "&minutes=0").statusCode());
        }
    }

    @Nested
    @DisplayName("bookings")
    class Bookings {

        @Test
        void booksARoomForWhoeverHoldsTheToken() {
            HttpResponse<String> response =
                api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(Status.CREATED, response.statusCode());
            assertTrue(response.body().contains("\"outcome\":\"confirmed\""));
            assertTrue(response.body().contains("\"userId\":\"u-1\""));
            assertTrue(response.body().contains("\"minutes\":60"));
            assertEquals(1, api.platform().stores().bookings().count());
        }

        @Test
        void takesTheAttendeeCountAndDefaultsItToOne() {
            String withParty = api.post("/bookings",
                "roomId=r-1&attendees=6&" + ApiTestServer.slot("10:00", 60), ada).body();
            assertTrue(withParty.contains("\"attendees\":6"));
            String withoutParty = api.post("/bookings",
                "roomId=r-2&" + ApiTestServer.slot("10:00", 60), blake).body();
            assertTrue(withoutParty.contains("\"attendees\":1"));
        }

        @Test
        void acceptsStartAndEndInsteadOfALength() {
            HttpResponse<String> response = api.post("/bookings",
                "roomId=r-1&start=2026-10-20T10:00:00Z&end=2026-10-20T11:30:00Z", ada);
            assertEquals(Status.CREATED, response.statusCode());
            assertTrue(response.body().contains("\"minutes\":90"));
        }

        @Test
        void aTakenSlotPutsTheCallerOnTheWaitlist() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            HttpResponse<String> response =
                api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);
            assertEquals(Status.ACCEPTED, response.statusCode());
            assertTrue(response.body().contains("\"outcome\":\"waitlisted\""));
            assertTrue(response.body().contains("\"position\":1"));
        }

        @Test
        void aRuleTheCallerBrokeComesBackWithItsOwnCode() {
            HttpResponse<String> response =
                api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 15), ada);
            assertEquals(Status.BAD_REQUEST, response.statusCode());
            assertTrue(response.body().contains("\"error\":\"interval-too-short\""));
        }

        @Test
        void anUnknownRoomIsNotFound() {
            HttpResponse<String> response =
                api.post("/bookings", "roomId=r-missing&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(Status.NOT_FOUND, response.statusCode());
            assertTrue(response.body().contains("\"error\":\"room-not-found\""));
        }

        @Test
        void anInactiveRoomIsAConflict() {
            HttpResponse<String> response =
                api.post("/bookings", "roomId=r-3&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(Status.CONFLICT, response.statusCode());
            assertTrue(response.body().contains("\"error\":\"room-inactive\""));
        }

        @Test
        void bookingNeedsAToken() {
            HttpResponse<String> response = api.postWithoutToken(
                "/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60));
            assertEquals(Status.UNAUTHORIZED, response.statusCode());
            assertEquals(0, api.platform().stores().bookings().count());
        }

        @Test
        void aTokenThatMeansNothingIsRefused() {
            HttpResponse<String> response = api.post(
                "/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), "not-a-token");
            assertEquals(Status.UNAUTHORIZED, response.statusCode());
        }

        @Test
        void aMissingParameterIsABadRequest() {
            assertEquals(Status.BAD_REQUEST, api.post("/bookings", "roomId=r-1", ada).statusCode());
            assertEquals(Status.BAD_REQUEST,
                api.post("/bookings", ApiTestServer.slot("10:00", 60), ada).statusCode());
        }

        @Test
        void showsABookingToItsOwner() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            HttpResponse<String> response = api.get("/bookings/" + id, ada);
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"CONFIRMED\""));
        }

        @Test
        void hidesABookingFromSomebodyElse() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(Status.FORBIDDEN, api.get("/bookings/" + id, blake).statusCode());
        }

        @Test
        void showsAnyBookingToAnAdministrator() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(Status.OK, api.get("/bookings/" + id, admin).statusCode());
        }

        @Test
        void anUnknownBookingIsNotFound() {
            assertEquals(Status.NOT_FOUND, api.get("/bookings/bk-missing", ada).statusCode());
        }

        @Test
        void listsTheCallersOwnBookings() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            bookAndGetId("roomId=r-2&" + ApiTestServer.slot("13:00", 60), blake);
            assertTrue(api.get("/bookings", ada).body().contains("\"count\":1"));
            assertTrue(api.get("/bookings", ada).body().contains("\"userId\":\"u-1\""));
        }

        @Test
        void refusesToListSomebodyElsesBookings() {
            assertEquals(Status.FORBIDDEN, api.get("/bookings?userId=u-1", blake).statusCode());
            assertEquals(Status.OK, api.get("/bookings?userId=u-1", admin).statusCode());
        }

        @Test
        void leavesCancelledBookingsOutUnlessAskedFor() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.delete("/bookings/" + id, ada);
            assertTrue(api.get("/bookings", ada).body().contains("\"count\":0"));
            assertTrue(api.get("/bookings?includeCancelled=yes", ada).body().contains("\"count\":1"));
        }
    }

    @Nested
    @DisplayName("cancellation and the waitlist")
    class Cancelling {

        @Test
        void theOwnerCancelsAndTheRoomIsFreeAgain() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            HttpResponse<String> response = api.delete("/bookings/" + id, ada);
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"CANCELLED\""));
            assertTrue(response.body().contains("\"promotions\":[]"));
        }

        @Test
        void somebodyElseCannotCancel() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            HttpResponse<String> response = api.delete("/bookings/" + id, blake);
            assertEquals(Status.FORBIDDEN, response.statusCode());
            assertTrue(response.body().contains("\"error\":\"not-booking-owner\""));
        }

        @Test
        void cancellingTwiceIsAConflict() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.delete("/bookings/" + id, ada);
            HttpResponse<String> response = api.delete("/bookings/" + id, ada);
            assertEquals(Status.CONFLICT, response.statusCode());
            assertTrue(response.body().contains("\"error\":\"booking-already-cancelled\""));
        }

        @Test
        void cancellingPromotesWhoeverWaitedLongest() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);

            HttpResponse<String> response = api.delete("/bookings/" + id, ada);

            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"waitlistEntryId\""));
            assertTrue(response.body().contains("\"userId\":\"u-2\""));
            assertTrue(api.get("/bookings", blake).body().contains("\"count\":1"));
        }

        @Test
        void showsARoomsQueue() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);
            HttpResponse<String> response = api.get("/waitlist?roomId=r-1", ada);
            assertEquals(Status.OK, response.statusCode());
            assertTrue(response.body().contains("\"count\":1"));
            assertTrue(response.body().contains("\"sequence\":1"));
        }

        @Test
        void showsTheCallersOwnQueueEntries() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);
            assertTrue(api.get("/waitlist", blake).body().contains("\"count\":1"));
            assertTrue(api.get("/waitlist", ada).body().contains("\"count\":0"));
            assertEquals(Status.FORBIDDEN, api.get("/waitlist?userId=u-2", ada).statusCode());
        }

        @Test
        void aWaiterGivesUpTheirPlace() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            String queued = api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake)
                .body();
            String entryId = bookingIdFrom(queued);

            assertEquals(Status.OK, api.delete("/waitlist/" + entryId, blake).statusCode());
            assertEquals(0, api.platform().stores().waitlist().count());
        }

        @Test
        void onlyTheWaiterMayGiveUpTheirOwnPlace() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            String entryId = bookingIdFrom(
                api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake).body());
            assertEquals(Status.FORBIDDEN, api.delete("/waitlist/" + entryId, ada).statusCode());
            assertEquals(Status.NOT_FOUND, api.delete("/waitlist/wl-missing", ada).statusCode());
        }

        @Test
        void theWaitlistNeedsAToken() {
            assertEquals(Status.UNAUTHORIZED, api.get("/waitlist?roomId=r-1").statusCode());
        }
    }

    @Nested
    @DisplayName("notifications")
    class Notifications {

        @Test
        void aConfirmedBookingQueuesAMessageForTheBooker() {
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            assertEquals(1, api.platform().outbox().size());
            assertEquals("u-1", api.platform().outbox().pending().get(0).recipientUserId());
        }

        @Test
        void cancellationAndPromotionEachQueueTheirOwnMessage() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);
            api.delete("/bookings/" + id, ada);
            assertEquals(3, api.platform().outbox().size());
        }

        @Test
        void theEventLogAndTheOutboxAgree() {
            String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
            api.delete("/bookings/" + id, ada);
            assertEquals(2, api.platform().stores().memoryEventLog().appended());
            assertEquals(2, api.platform().outbox().size());
            assertEquals(0, api.platform().notifications().skippedCount());
        }
    }
}
