# BOUNDARIES.md

## Milestone 1: The architecture as built

**The sketch.** Arrows are runtime calls or data writes; store labels name the data written.

```mermaid
flowchart TB
  HTTP[http-api: CampusReserveServer] --> Platform[http-api: CampusReservePlatform] --> Router[Router]
  CLI[cli: CampusReserveCli] --> CliContext[cli: CliContext]
  Router --> Rooms[RoomsHandler] --> Catalog[core-domain: RoomCatalog] --> RoomPort[RoomStore port]
  Rooms --> Availability[core-domain: AvailabilityService] --> BookingPort[BookingStore port]
  Router --> Bookings[BookingsHandler] --> Auth[RequestAuth] --> Accounts[accounts: AuthorizationService + TokenService]
  Bookings --> Service[core-domain: BookingService]
  CliContext --> Service
  Service --> RoomPort
  Service --> BookingPort
  Service --> UserPort[UserStore port]
  Service --> WaitPort[WaitlistStore port]
  Service --> Events[EventPublisher port]
  RoomPort --> StorageRooms[storage: rooms]
  BookingPort --> StorageBookings[storage: bookings]
  UserPort --> StorageUsers[storage: users]
  WaitPort --> StorageWaitlist[storage: waitlist]
  Events --> Log[storage: in-memory event log]
  Events --> Notices[notifications: outbox]
  CliContext --> Billing[billing: invoices]
  CLI --> Export[cli: export-events] --> File[events.jsonl] --> Analytics[analytics: reports]
```

Availability path: `CampusReserveServer -> CampusReservePlatform -> Router -> RoomsHandler.availability -> RoomCatalog + AvailabilityService -> RoomStore + BookingStore -> storage`.

Booking path: `CampusReserveServer -> CampusReservePlatform -> Router -> BookingsHandler.create -> RequestAuth -> AuthorizationService/TokenService -> BookingService.requestBooking -> BookingStore/RoomStore/UserStore/WaitlistStore -> EventLogPublisher -> in-memory event log and NotificationService`.

**Where my sketch and `ARCHITECTURE.md` disagree, and how I checked.**

1. The document calls the CLI entry `BookCommand`; source has `BookingCommands.Book`. `rg -n "BookCommand|BookingCommands" .` finds the stale diagram label, `BookingCommands.java`, and registrations in `CampusReserveCli.java` lines 112--115; no `BookCommand` class exists.
2. The document includes the durable `JsonlFileEventLogWriter` path; the shipped HTTP runtime sketch ends in `InMemoryEventLog`. `CampusReservePlatform` line 44 calls `StoreRegistry.inMemory()`, which constructs `InMemoryEventLog` at `StoreRegistry` line 33. `rg -n "withEventLogFile|JsonlFileEventLogWriter"` finds only the alternate implementation and tests, not an HTTP-platform caller.

## Milestone 2: Run it as copies

Pending implementation of the Compose stack and k6 checks.

## Milestone 3: One boundary

Pending selection after the copy checks.
