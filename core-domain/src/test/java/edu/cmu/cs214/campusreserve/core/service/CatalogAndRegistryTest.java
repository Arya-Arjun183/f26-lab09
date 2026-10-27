package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import edu.cmu.cs214.campusreserve.core.support.InMemoryStores;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CatalogAndRegistryTest {

    @Nested
    @DisplayName("room catalog")
    class Catalog {

        private RoomCatalog catalog;

        @BeforeEach
        void setUp() {
            catalog = new RoomCatalog(new InMemoryStores.Rooms());
            catalog.register(new Room("r-1", "Study A", "Sorrells", 4, RoomKind.STUDY_ROOM, true));
            catalog.register(new Room("r-2", "Study B", "Sorrells", 8, RoomKind.STUDY_ROOM, true));
            catalog.register(new Room("r-3", "Lecture 1", "Wean", 60, RoomKind.CLASSROOM, true));
            catalog.register(new Room("r-4", "Bench Lab", "Hamerschlag", 12, RoomKind.LAB, true));
        }

        @Test
        void findsRoomsById() {
            assertEquals("Study A", catalog.find("r-1").orElseThrow().name());
            assertTrue(catalog.find("r-missing").isEmpty());
        }

        @Test
        void requireFailsLoudlyOnAnUnknownRoom() {
            assertThrows(IllegalArgumentException.class, () -> catalog.require("r-missing"));
            assertEquals("r-1", catalog.require("r-1").id());
        }

        @Test
        void deactivationHidesTheRoomFromActiveListings() {
            assertTrue(catalog.deactivate("r-1"));
            assertEquals(4, catalog.listAll().size());
            assertEquals(3, catalog.listActive().size());
            assertFalse(catalog.find("r-1").orElseThrow().active());
        }

        @Test
        void reactivationPutsItBack() {
            catalog.deactivate("r-1");
            assertTrue(catalog.reactivate("r-1"));
            assertEquals(4, catalog.listActive().size());
        }

        @Test
        void deactivatingAnUnknownRoomReportsFailure() {
            assertFalse(catalog.deactivate("r-missing"));
            assertFalse(catalog.reactivate("r-missing"));
        }

        @Test
        void searchFiltersByCapacity() {
            List<Room> found = catalog.search(RoomSearchCriteria.forParty(10));
            assertEquals(List.of("r-4", "r-3"), found.stream().map(Room::id).toList());
        }

        @Test
        void searchFiltersByKindAndBuilding() {
            assertEquals(List.of("r-1", "r-2"), catalog.search(
                RoomSearchCriteria.any().withKind(RoomKind.STUDY_ROOM)).stream().map(Room::id).toList());
            assertEquals(List.of("r-3"), catalog.search(
                RoomSearchCriteria.any().withBuilding("wean")).stream().map(Room::id).toList());
        }

        @Test
        void searchSkipsInactiveRooms() {
            catalog.deactivate("r-1");
            assertEquals(List.of("r-2"), catalog.search(
                RoomSearchCriteria.any().withKind(RoomKind.STUDY_ROOM).withBuilding("Sorrells"))
                .stream().map(Room::id).toList());
        }

        @Test
        void findsTheSmallestRoomThatFits() {
            assertEquals("r-2", catalog.smallestThatFits(5).orElseThrow().id());
            assertEquals("r-3", catalog.smallestThatFits(50).orElseThrow().id());
            assertTrue(catalog.smallestThatFits(500).isEmpty());
        }

        @Test
        void rejectsANonPositivePartySize() {
            assertThrows(IllegalArgumentException.class, () -> RoomSearchCriteria.forParty(0));
        }

        @Test
        void listsBuildingsWithActiveRooms() {
            assertEquals(List.of("Hamerschlag", "Sorrells", "Wean"), catalog.buildings());
            catalog.deactivate("r-3");
            assertEquals(List.of("Hamerschlag", "Sorrells"), catalog.buildings());
        }

        @Test
        void countsActiveRoomsByKind() {
            List<RoomCatalog.KindCount> counts = catalog.countsByKind();
            assertEquals(3, counts.size());
            assertEquals(RoomKind.STUDY_ROOM, counts.get(0).kind());
            assertEquals(2, counts.get(0).count());
        }
    }

    @Nested
    @DisplayName("user registry")
    class Registry {

        private UserRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new UserRegistry(new InMemoryStores.Users());
            registry.register(Fixtures.user("u-1"));
            registry.register(Fixtures.user("u-2"));
        }

        @Test
        void findsListsAndCountsUsers() {
            assertEquals("User u-1", registry.find("u-1").orElseThrow().name());
            assertTrue(registry.find("u-missing").isEmpty());
            assertTrue(registry.exists("u-2"));
            assertFalse(registry.exists("u-missing"));
            assertEquals(2, registry.count());
            assertEquals(List.of("u-1", "u-2"), registry.listAll().stream().map(u -> u.id()).toList());
        }

        @Test
        void requireFailsLoudlyOnAnUnknownUser() {
            assertThrows(IllegalArgumentException.class, () -> registry.require("u-missing"));
        }

        @Test
        void findsUsersByAddressWithoutRegardToCase() {
            assertEquals("u-1", registry.findByEmail("U-1@ANDREW.CMU.EDU").orElseThrow().id());
            assertTrue(registry.findByEmail("nobody@andrew.cmu.edu").isEmpty());
        }

        @Test
        void registeringTheSameIdReplacesTheUser() {
            registry.register(new edu.cmu.cs214.campusreserve.core.domain.User("u-1", "Renamed", "r@x.edu"));
            assertEquals("Renamed", registry.require("u-1").name());
            assertEquals(2, registry.count());
        }
    }
}
