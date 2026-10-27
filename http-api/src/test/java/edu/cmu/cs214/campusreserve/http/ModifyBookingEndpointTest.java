package edu.cmu.cs214.campusreserve.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Changing a booking over the wire. */
class ModifyBookingEndpointTest {

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

    @Test
    void theOwnerMovesTheirBooking() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), ada);

        assertEquals(Status.OK, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"start\":\"2026-10-20T13:00:00Z\""), response.body());
        assertTrue(response.body().contains("\"promotions\":[]"), response.body());
    }

    @Test
    void thePartySizeCanChangeOnItsOwn() {
        String id =
            bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60) + "&attendees=2", ada);

        HttpResponse<String> response = api.method("PATCH", "/bookings/" + id, "attendees=5", ada);

        assertEquals(Status.OK, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"attendees\":5"), response.body());
        assertTrue(response.body().contains("\"start\":\"2026-10-20T10:00:00Z\""), response.body());
    }

    @Test
    void theHistoryIsOnTheBookingTheReadSurfaceAlreadyHas() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), ada);

        HttpResponse<String> response = api.get("/bookings/" + id, ada);

        assertEquals(Status.OK, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"previousStart\":\"2026-10-20T10:00:00Z\""),
            response.body());
        assertTrue(response.body().contains("\"previousEnd\":\"2026-10-20T11:00:00Z\""),
            response.body());
    }

    @Test
    void anAdministratorReadingTheBookingSeesTheHistoryToo() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), ada);

        HttpResponse<String> response = api.get("/bookings/" + id, admin);

        assertEquals(Status.OK, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"previousStart\":\"2026-10-20T10:00:00Z\""),
            response.body());
    }

    @Test
    void anUntouchedBookingCarriesAnEmptyHistory() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        assertTrue(api.get("/bookings/" + id, ada).body().contains("\"modifications\":[]"));
    }

    @Test
    void somebodyElsesBookingIsForbidden() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), blake);

        assertEquals(Status.FORBIDDEN, response.statusCode(), response.body());
        assertTrue(api.get("/bookings/" + id, ada).body()
            .contains("\"start\":\"2026-10-20T10:00:00Z\""));
    }

    @Test
    void anAdministratorIsNotAnOwner() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), admin);

        assertEquals(Status.FORBIDDEN, response.statusCode(), response.body());
    }

    @Test
    void aTakenSlotIsAConflictAndTheBookingStands() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        bookAndGetId("roomId=r-1&" + ApiTestServer.slot("13:00", 60), blake);

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), ada);

        assertEquals(Status.CONFLICT, response.statusCode(), response.body());
        assertTrue(response.body().contains("room-unavailable"), response.body());
        assertTrue(api.get("/bookings/" + id, ada).body().contains("\"modifications\":[]"));
    }

    @Test
    void anIntervalTheRulesRefuseIsABadRequest() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("05:00", 60), ada);

        assertEquals(Status.BAD_REQUEST, response.statusCode(), response.body());
        assertTrue(response.body().contains("outside-opening-hours"), response.body());
    }

    @Test
    void anUnknownBookingIsNotFound() {
        HttpResponse<String> response =
            api.method("PATCH", "/bookings/bk-missing", ApiTestServer.slot("13:00", 60), ada);
        assertEquals(Status.NOT_FOUND, response.statusCode(), response.body());
    }

    @Test
    void aChangeWithoutATokenIsRefused() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), "not-a-token");
        assertEquals(Status.UNAUTHORIZED, response.statusCode(), response.body());
    }

    @Test
    void aNonPositivePartySizeIsABadRequest() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        HttpResponse<String> response = api.method("PATCH", "/bookings/" + id, "attendees=0", ada);
        assertEquals(Status.BAD_REQUEST, response.statusCode(), response.body());
    }

    @Test
    void freedTimeReachesWhoeverWasWaiting() {
        String id = bookAndGetId("roomId=r-1&" + ApiTestServer.slot("10:00", 60), ada);
        HttpResponse<String> waited =
            api.post("/bookings", "roomId=r-1&" + ApiTestServer.slot("10:00", 60), blake);
        assertEquals(Status.ACCEPTED, waited.statusCode(), waited.body());

        HttpResponse<String> response =
            api.method("PATCH", "/bookings/" + id, ApiTestServer.slot("13:00", 60), ada);

        assertEquals(Status.OK, response.statusCode(), response.body());
        assertFalse(response.body().contains("\"promotions\":[]"), response.body());
        assertTrue(api.get("/bookings", blake).body().contains("\"start\":\"2026-10-20T10:00:00Z\""));
    }

    private String bookAndGetId(String form, String token) {
        HttpResponse<String> response = api.post("/bookings", form, token);
        assertEquals(Status.CREATED, response.statusCode(), response.body());
        String body = response.body();
        int idAt = body.indexOf("\"id\":\"");
        assertTrue(idAt >= 0, "No booking id in " + body);
        int start = idAt + "\"id\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }
}
