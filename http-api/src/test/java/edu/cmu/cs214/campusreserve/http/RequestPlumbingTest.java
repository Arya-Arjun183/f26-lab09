package edu.cmu.cs214.campusreserve.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequestPlumbingTest {

    private static final ApiHandler NOTHING = request -> ApiResponse.ok(JsonObject.create());

    @Nested
    @DisplayName("json writing")
    class Json {

        @Test
        void writesFieldsInOrder() {
            assertEquals("{\"a\":\"1\",\"b\":2,\"c\":true}",
                JsonObject.create().field("a", "1").field("b", 2).field("c", true).render());
        }

        @Test
        void writesAnEmptyObjectAndAnEmptyArray() {
            assertEquals("{}", JsonObject.create().render());
            assertEquals("[]", JsonArray.create().render());
            assertTrue(JsonArray.create().isEmpty());
        }

        @Test
        void escapesQuotesAndNewlines() {
            assertEquals("{\"name\":\"say \\\"hi\\\"\"}",
                JsonObject.create().field("name", "say \"hi\"").render());
            assertEquals("{\"name\":\"a\\nb\"}", JsonObject.create().field("name", "a\nb").render());
        }

        @Test
        void writesNullForAMissingValueAndSkipsOptionalOnes() {
            assertEquals("{\"a\":null}", JsonObject.create().field("a", (String) null).render());
            assertEquals("{}", JsonObject.create().optionalField("a", null).render());
        }

        @Test
        void nestsObjectsAndArrays() {
            JsonObject inner = JsonObject.create().field("id", "r-1");
            String rendered = JsonObject.create()
                .field("room", inner)
                .field("tags", JsonArray.create().add("quiet").add(3L))
                .render();
            assertEquals("{\"room\":{\"id\":\"r-1\"},\"tags\":[\"quiet\",3]}", rendered);
        }

        @Test
        void writesFixedPointNumbers() {
            assertEquals("{\"rate\":0.2500}", JsonObject.create().field("rate", 0.25, 4).render());
            assertThrows(IllegalArgumentException.class,
                () -> JsonObject.create().field("rate", 0.25, -1));
        }

        @Test
        void buildsAnArrayFromRecords() {
            JsonArray array = JsonArray.of(List.of("a", "b"), value -> JsonObject.create().field("v", value));
            assertEquals(2, array.size());
            assertEquals("[{\"v\":\"a\"},{\"v\":\"b\"}]", array.render());
            assertEquals("[\"x\",\"y\"]", JsonArray.ofStrings(List.of("x", "y")).render());
        }
    }

    @Nested
    @DisplayName("form and query values")
    class Values {

        @Test
        void parsesPairs() {
            assertEquals(Map.of("a", List.of("1"), "b", List.of("2")), FormValues.parse("a=1&b=2"));
        }

        @Test
        void toleratesALeadingQuestionMarkAndEmptyInput() {
            assertEquals(List.of("1"), FormValues.parse("?a=1").get("a"));
            assertTrue(FormValues.parse("").isEmpty());
            assertTrue(FormValues.parse(null).isEmpty());
        }

        @Test
        void keepsEveryValueForARepeatedName() {
            assertEquals(List.of("1", "2"), FormValues.parse("a=1&a=2").get("a"));
        }

        @Test
        void decodesPercentEscapesAndPlusSigns() {
            assertEquals(List.of("Sorrells 205"), FormValues.parse("room=Sorrells+205").get("room"));
            assertEquals(List.of("2026-10-20T10:00:00Z"),
                FormValues.parse("start=2026-10-20T10%3A00%3A00Z").get("start"));
        }

        @Test
        void aNameWithNoValueYieldsAnEmptyString() {
            assertEquals(List.of(""), FormValues.parse("flag").get("flag"));
        }

        @Test
        void mergesQueryAndBody() {
            Map<String, List<String>> merged =
                FormValues.merge(FormValues.parse("a=1"), FormValues.parse("a=2&b=3"));
            assertEquals(List.of("1", "2"), merged.get("a"));
            assertEquals(List.of("3"), merged.get("b"));
        }
    }

    @Nested
    @DisplayName("request context")
    class Context {

        private RequestContext with(String query) {
            return new RequestContext("GET", "/x", Map.of("bookingId", "bk-1"),
                FormValues.parse(query), Map.of("Authorization", "Bearer abc"));
        }

        @Test
        void readsPathParameters() {
            assertEquals("bk-1", with("").pathParam("bookingId"));
            assertThrows(IllegalStateException.class, () -> with("").pathParam("roomId"));
        }

        @Test
        void treatsABlankParameterAsAbsent() {
            assertTrue(with("a=%20").param("a").isEmpty());
            assertThrows(ApiException.class, () -> with("a=%20").requireParam("a"));
        }

        @Test
        void aMissingRequiredParameterIsABadRequest() {
            ApiException failure = assertThrows(ApiException.class, () -> with("").requireParam("roomId"));
            assertEquals(Status.BAD_REQUEST, failure.status());
            assertEquals("missing-parameter", failure.code());
        }

        @Test
        void readsWholeNumbers() {
            assertEquals(4, with("attendees=4").requireInt("attendees"));
            assertEquals(1, with("").intParam("attendees", 1));
            assertThrows(ApiException.class, () -> with("attendees=lots").requireInt("attendees"));
        }

        @Test
        void readsInstantsAndDates() {
            assertEquals(Instant.parse("2026-10-20T10:00:00Z"),
                with("start=2026-10-20T10:00:00Z").requireInstant("start"));
            assertEquals(LocalDate.parse("2026-10-20"), with("date=2026-10-20").requireDate("date"));
            assertThrows(ApiException.class, () -> with("start=soon").requireInstant("start"));
            assertThrows(ApiException.class, () -> with("date=soon").requireDate("date"));
        }

        @Test
        void buildsAnIntervalFromStartAndEnd() {
            TimeInterval interval = with("start=2026-10-20T10:00:00Z&end=2026-10-20T11:00:00Z")
                .requireInterval();
            assertEquals(60, interval.durationMinutes());
        }

        @Test
        void buildsAnIntervalFromStartAndMinutes() {
            assertEquals(90, with("start=2026-10-20T10:00:00Z&minutes=90").requireInterval()
                .durationMinutes());
        }

        @Test
        void anImpossibleIntervalIsABadRequestNotACrash() {
            ApiException failure = assertThrows(ApiException.class,
                () -> with("start=2026-10-20T11:00:00Z&end=2026-10-20T10:00:00Z").requireInterval());
            assertEquals(Status.BAD_REQUEST, failure.status());
        }

        @Test
        void readsHeadersWithoutRegardToCase() {
            assertEquals("Bearer abc", with("").header("authorization").orElseThrow());
            assertEquals("Bearer abc", with("").authorizationHeader().orElseThrow());
            assertTrue(with("").header("X-Missing").isEmpty());
        }
    }

    @Nested
    @DisplayName("router")
    class Routing {

        private final Router router = new Router()
            .get("/rooms", NOTHING)
            .get("/rooms/{roomId}/availability", NOTHING)
            .get("/rooms/{roomId}", NOTHING)
            .post("/bookings", NOTHING)
            .delete("/bookings/{bookingId}", NOTHING);

        @Test
        void matchesAStaticPath() {
            assertTrue(router.lookup("GET", "/rooms") instanceof Router.Lookup.Found);
            assertEquals(5, router.size());
            assertEquals(5, router.routes().size());
        }

        @Test
        void capturesPathParameters() {
            Router.Lookup.Found found = (Router.Lookup.Found) router.lookup("GET", "/rooms/r-1");
            assertEquals(Map.of("roomId", "r-1"), found.match().pathParams());
        }

        @Test
        void prefersTheRouteRegisteredFirst() {
            Router.Lookup.Found found =
                (Router.Lookup.Found) router.lookup("GET", "/rooms/r-1/availability");
            assertEquals("/rooms/{roomId}/availability", found.match().route().pattern());
        }

        @Test
        void ignoresATrailingSlash() {
            assertTrue(router.lookup("GET", "/rooms/") instanceof Router.Lookup.Found);
        }

        @Test
        void isNotCaseSensitiveAboutTheMethod() {
            assertTrue(router.lookup("get", "/rooms") instanceof Router.Lookup.Found);
        }

        @Test
        void tellsAWrongMethodApartFromAMissingPath() {
            assertTrue(router.lookup("DELETE", "/rooms") instanceof Router.Lookup.WrongMethod);
            assertTrue(router.lookup("GET", "/nowhere") instanceof Router.Lookup.NoRoute);
        }

        @Test
        void namesTheMethodsThatWouldHaveWorked() {
            Router.Lookup.WrongMethod wrong = (Router.Lookup.WrongMethod) router.lookup("PUT", "/bookings");
            assertEquals(List.of("POST"), wrong.allowed());
        }

        @Test
        void doesNotMatchAcrossSegmentBoundaries() {
            assertTrue(router.lookup("GET", "/rooms/r-1/extra/deep") instanceof Router.Lookup.NoRoute);
        }
    }

    @Nested
    @DisplayName("error mapping")
    class Errors {

        @Test
        void missingRecordsAreNotFound() {
            assertEquals(Status.NOT_FOUND, ErrorMapping.statusFor(RejectionReason.ROOM_NOT_FOUND));
            assertEquals(Status.NOT_FOUND, ErrorMapping.statusFor(RejectionReason.BOOKING_NOT_FOUND));
        }

        @Test
        void someoneElsesBookingIsForbidden() {
            assertEquals(Status.FORBIDDEN, ErrorMapping.statusFor(RejectionReason.NOT_BOOKING_OWNER));
        }

        @Test
        void aCollisionWithTheCalendarIsAConflict() {
            assertEquals(Status.CONFLICT, ErrorMapping.statusFor(RejectionReason.ROOM_UNAVAILABLE));
            assertEquals(Status.CONFLICT, ErrorMapping.statusFor(RejectionReason.USER_DOUBLE_BOOKED));
            assertTrue(ErrorMapping.isRetryable(RejectionReason.BOOKING_ALREADY_STARTED));
        }

        @Test
        void aRuleTheCallerBrokeIsABadRequest() {
            assertEquals(Status.BAD_REQUEST, ErrorMapping.statusFor(RejectionReason.INTERVAL_TOO_LONG));
            assertEquals(Status.BAD_REQUEST, ErrorMapping.statusFor(RejectionReason.OUTSIDE_OPENING_HOURS));
            assertFalse(ErrorMapping.isRetryable(RejectionReason.INTERVAL_NOT_ALIGNED));
        }

        @Test
        void aRejectionKeepsItsOwnCodeOnTheWire() {
            ApiResponse response = ApiException.of(RejectionReason.ROOM_UNAVAILABLE).toResponse();
            assertEquals(Status.CONFLICT, response.status());
            assertTrue(response.body().contains("\"error\":\"room-unavailable\""));
            assertFalse(response.isSuccess());
        }

        @Test
        void responsesCarryTheJsonContentType() {
            assertEquals(ApiResponse.JSON, ApiResponse.ok(JsonObject.create()).contentType());
            assertTrue(ApiResponse.created(JsonObject.create()).isSuccess());
            assertThrows(IllegalArgumentException.class, () -> new ApiResponse(99, "{}", ApiResponse.JSON));
        }

        @Test
        void aListResponseCarriesItsCount() {
            ApiResponse response = ApiResponse.ok("rooms", JsonArray.ofStrings(List.of("a", "b")));
            assertTrue(response.body().contains("\"count\":2"));
        }
    }
}
