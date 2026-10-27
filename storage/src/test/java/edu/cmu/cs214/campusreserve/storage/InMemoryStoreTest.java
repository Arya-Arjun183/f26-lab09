package edu.cmu.cs214.campusreserve.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingStatus;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.domain.User;
import edu.cmu.cs214.campusreserve.core.domain.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryStoreTest {

    @Nested
    @DisplayName("room store")
    class Rooms {

        private InMemoryRoomStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryRoomStore();
            store.save(StorageFixtures.room("r-2", "Wean", 40, RoomKind.CLASSROOM));
            store.save(StorageFixtures.room("r-1", "Sorrells", 8, RoomKind.STUDY_ROOM));
            store.save(StorageFixtures.room("r-3", "Wean", 4, RoomKind.STUDY_ROOM));
        }

        @Test
        void findsRoomsByIdAndListsThemInIdOrder() {
            assertEquals("Room r-1", store.findById("r-1").orElseThrow().name());
            assertTrue(store.findById("r-missing").isEmpty());
            assertEquals(List.of("r-1", "r-2", "r-3"), store.findAll().stream().map(Room::id).toList());
            assertEquals(3, store.count());
        }

        @Test
        void savingTheSameIdReplacesTheRoom() {
            store.save(StorageFixtures.room("r-1", "Sorrells", 12, RoomKind.STUDY_ROOM));
            assertEquals(12, store.findById("r-1").orElseThrow().capacity());
            assertEquals(3, store.count());
        }

        @Test
        void filtersInactiveRoomsOutOfTheActiveListing() {
            store.save(store.findById("r-2").orElseThrow().withActive(false));
            assertEquals(List.of("r-1", "r-3"), store.findActive().stream().map(Room::id).toList());
            assertEquals(3, store.findAll().size());
        }

        @Test
        void findsRoomsByBuildingWithoutRegardToCase() {
            assertEquals(List.of("r-2", "r-3"), store.findByBuilding("wean").stream().map(Room::id).toList());
        }

        @Test
        void findsFittingRoomsSmallestFirst() {
            assertEquals(List.of("r-3", "r-1", "r-2"), store.findFitting(4).stream().map(Room::id).toList());
            assertEquals(List.of("r-2"), store.findFitting(20).stream().map(Room::id).toList());
        }

        @Test
        void fittingRoomsSkipInactiveOnes() {
            store.save(store.findById("r-3").orElseThrow().withActive(false));
            assertEquals(List.of("r-1", "r-2"), store.findFitting(4).stream().map(Room::id).toList());
        }

        @Test
        void removeAndClearEmptyTheStore() {
            assertTrue(store.remove("r-1"));
            assertFalse(store.remove("r-1"));
            assertFalse(store.contains("r-1"));
            store.clear();
            assertTrue(store.isEmpty());
        }
    }

    @Nested
    @DisplayName("user store")
    class Users {

        private InMemoryUserStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryUserStore();
            store.save(StorageFixtures.user("u-2"));
            store.save(StorageFixtures.user("u-1"));
        }

        @Test
        void listsUsersInIdOrder() {
            assertEquals(List.of("u-1", "u-2"), store.findAll().stream().map(User::id).toList());
            assertEquals(2, store.count());
        }

        @Test
        void reportsExistence() {
            assertTrue(store.exists("u-1"));
            assertFalse(store.exists("u-missing"));
        }

        @Test
        void findsUsersByAddressWithoutRegardToCase() {
            assertEquals("u-1", store.findByEmail("U-1@ANDREW.CMU.EDU").orElseThrow().id());
            assertTrue(store.findByEmail("nobody@andrew.cmu.edu").isEmpty());
            assertTrue(store.findByEmail(null).isEmpty());
        }

        @Test
        void removesUsers() {
            assertTrue(store.remove("u-1"));
            assertEquals(1, store.count());
        }
    }

    @Nested
    @DisplayName("booking store")
    class Bookings {

        private InMemoryBookingStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryBookingStore();
            store.save(StorageFixtures.booking("bk-3", "r-1", "u-2", "14:00", "15:00"));
            store.save(StorageFixtures.booking("bk-1", "r-1", "u-1", "09:00", "10:00"));
            store.save(StorageFixtures.booking("bk-2", "r-2", "u-1", "11:00", "12:00"));
        }

        @Test
        void listsBookingsInStartOrder() {
            assertEquals(List.of("bk-1", "bk-2", "bk-3"), store.findAll().stream().map(Booking::id).toList());
        }

        @Test
        void filtersByRoomAndByUser() {
            assertEquals(List.of("bk-1", "bk-3"), store.findByRoom("r-1").stream().map(Booking::id).toList());
            assertEquals(List.of("bk-1", "bk-2"), store.findByUser("u-1").stream().map(Booking::id).toList());
            assertTrue(store.findByRoom("r-missing").isEmpty());
        }

        @Test
        void cancelledBookingsStayButLeaveTheActiveListings() {
            store.save(store.findById("bk-1").orElseThrow().cancelled());
            assertEquals(3, store.count());
            assertEquals(List.of("bk-3"), store.findActiveByRoom("r-1").stream().map(Booking::id).toList());
            assertEquals(List.of("bk-2"), store.findActiveByUser("u-1").stream().map(Booking::id).toList());
            assertEquals(List.of("bk-1"), store.findByStatus(BookingStatus.CANCELLED).stream()
                .map(Booking::id).toList());
            assertEquals(2, store.countByStatus(BookingStatus.CONFIRMED));
        }

        @Test
        void findsBookingsStartingFromAnInstant() {
            Instant noon = Instant.parse("2026-10-20T12:00:00Z");
            assertEquals(List.of("bk-3"), store.findStartingFrom(noon).stream().map(Booking::id).toList());
        }

        @Test
        void findsUpcomingBookingsInOneRoom() {
            Instant tenAm = Instant.parse("2026-10-20T10:00:00Z");
            assertEquals(List.of("bk-3"), store.findUpcomingByRoom("r-1", tenAm).stream()
                .map(Booking::id).toList());
        }

        @Test
        void upcomingSkipsCancelledBookings() {
            store.save(store.findById("bk-3").orElseThrow().cancelled());
            Instant tenAm = Instant.parse("2026-10-20T10:00:00Z");
            assertTrue(store.findUpcomingByRoom("r-1", tenAm).isEmpty());
        }

        @Test
        void removesBookings() {
            assertTrue(store.remove("bk-2"));
            assertEquals(2, store.count());
        }
    }

    @Nested
    @DisplayName("waitlist store")
    class Waitlist {

        private InMemoryWaitlistStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryWaitlistStore();
        }

        @Test
        void handsOutIncreasingArrivalNumbers() {
            assertEquals(1, store.nextSequence());
            assertEquals(2, store.nextSequence());
            assertEquals(3, store.nextSequence());
            assertEquals(3, store.issuedSequences());
        }

        @Test
        void listsEntriesInArrivalOrderNoMatterTheInsertOrder() {
            store.save(StorageFixtures.entry("wl-3", "r-1", "u-3", 3));
            store.save(StorageFixtures.entry("wl-1", "r-1", "u-1", 1));
            store.save(StorageFixtures.entry("wl-2", "r-1", "u-2", 2));
            assertEquals(List.of("wl-1", "wl-2", "wl-3"),
                store.findByRoom("r-1").stream().map(WaitlistEntry::id).toList());
            assertEquals(List.of("wl-1", "wl-2", "wl-3"),
                store.findAll().stream().map(WaitlistEntry::id).toList());
        }

        @Test
        void filtersByRoomAndByUser() {
            store.save(StorageFixtures.entry("wl-1", "r-1", "u-1", 1));
            store.save(StorageFixtures.entry("wl-2", "r-2", "u-1", 2));
            assertEquals(1, store.countForRoom("r-1"));
            assertEquals(2, store.findByUser("u-1").size());
            assertTrue(store.findByUser("u-missing").isEmpty());
        }

        @Test
        void peeksAtWhoeverHasWaitedLongest() {
            store.save(StorageFixtures.entry("wl-2", "r-1", "u-2", 2));
            store.save(StorageFixtures.entry("wl-1", "r-1", "u-1", 1));
            assertEquals("wl-1", store.peekRoom("r-1").orElseThrow().id());
            assertTrue(store.peekRoom("r-missing").isEmpty());
        }

        @Test
        void removingAnEntryReportsWhetherThereWasOne() {
            store.save(StorageFixtures.entry("wl-1", "r-1", "u-1", 1));
            assertTrue(store.remove("wl-1"));
            assertFalse(store.remove("wl-1"));
            assertEquals(0, store.count());
        }

        @Test
        void arrivalNumbersAreNotReusedAfterARemoval() {
            long first = store.nextSequence();
            store.save(StorageFixtures.entry("wl-1", "r-1", "u-1", first));
            store.remove("wl-1");
            assertTrue(store.nextSequence() > first);
        }
    }
}
