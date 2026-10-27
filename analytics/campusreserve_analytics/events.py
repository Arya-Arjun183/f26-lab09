"""Reading the event log.

The platform writes one JSON object per line to events.jsonl. Every line carries
the same core fields:

    event, occurredAt, bookingId, roomId, userId, start, end, attendees

Promotion lines add waitlistEntryId and waitlistSequence, and modification lines
add previousStart, previousEnd, and previousAttendees. All instants are ISO-8601
in UTC and land on whole minutes.

Nothing here mutates the log. The reader parses lines into Event records, and
build_ledger folds those events into one record per booking, which is what the
reports work from.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Iterable, Iterator, Mapping, Sequence

BOOKING_CREATED = "booking-created"
BOOKING_CANCELLED = "booking-cancelled"
BOOKING_MODIFIED = "booking-modified"
WAITLIST_PROMOTED = "waitlist-promoted"

#: The event types the platform writes, in the order it declares them.
KNOWN_EVENT_TYPES = (BOOKING_CREATED, BOOKING_CANCELLED, BOOKING_MODIFIED, WAITLIST_PROMOTED)

#: Event types that put a booking on a room's calendar.
HOLDING_EVENT_TYPES = (BOOKING_CREATED, WAITLIST_PROMOTED)

_REQUIRED_FIELDS = (
    "event",
    "occurredAt",
    "bookingId",
    "roomId",
    "userId",
    "start",
    "end",
    "attendees",
)


class EventLogError(ValueError):
    """A line in the event log could not be read.

    The message always names the line number so the operator can go look at it.
    """

    def __init__(self, message: str, line_number: int | None = None) -> None:
        self.line_number = line_number
        if line_number is None:
            super().__init__(message)
        else:
            super().__init__(f"line {line_number}: {message}")


@dataclass(frozen=True)
class Event:
    """One line of the event log."""

    event_type: str
    occurred_at: datetime
    booking_id: str
    room_id: str
    user_id: str
    start: datetime
    end: datetime
    attendees: int
    waitlist_entry_id: str | None = None
    waitlist_sequence: int | None = None

    @property
    def duration_minutes(self) -> int:
        """How long the booked interval runs, in whole minutes."""
        return int((self.end - self.start).total_seconds() // 60)

    @property
    def start_date(self) -> date:
        """The UTC date the booked interval starts on."""
        return self.start.date()

    @property
    def start_hour(self) -> int:
        """The UTC hour the booked interval starts in, 0 through 23."""
        return self.start.hour

    @property
    def puts_booking_on_calendar(self) -> bool:
        """Does this event mean a booking now holds a room?"""
        return self.event_type in HOLDING_EVENT_TYPES

    @property
    def came_from_waitlist(self) -> bool:
        """Was this booking promoted rather than booked outright?"""
        return self.event_type == WAITLIST_PROMOTED

    @property
    def changes_a_booking(self) -> bool:
        """Does this event restate an interval a booking already held?"""
        return self.event_type == BOOKING_MODIFIED


@dataclass
class BookingRecord:
    """What the log says about one booking, after every line about it is folded in."""

    booking_id: str
    room_id: str
    user_id: str
    start: datetime
    end: datetime
    attendees: int
    created_at: datetime
    from_waitlist: bool = False
    waitlist_entry_id: str | None = None
    cancelled_at: datetime | None = None

    @property
    def duration_minutes(self) -> int:
        """How long the booking runs, in whole minutes."""
        return int((self.end - self.start).total_seconds() // 60)

    @property
    def start_date(self) -> date:
        """The UTC date the booking starts on."""
        return self.start.date()

    @property
    def start_hour(self) -> int:
        """The UTC hour the booking starts in."""
        return self.start.hour

    @property
    def is_cancelled(self) -> bool:
        """Did the booking give its room back?"""
        return self.cancelled_at is not None

    @property
    def holds_room(self) -> bool:
        """Does the booking still hold its room?"""
        return self.cancelled_at is None


@dataclass
class Ledger:
    """Every booking the log mentions, plus what the reader could not place."""

    bookings: dict[str, BookingRecord] = field(default_factory=dict)
    cancellations_without_a_booking: list[str] = field(default_factory=list)
    event_counts: dict[str, int] = field(default_factory=dict)

    def held(self) -> list[BookingRecord]:
        """Bookings that still hold a room, in start order."""
        return sorted(
            (record for record in self.bookings.values() if record.holds_room),
            key=lambda record: (record.start, record.booking_id),
        )

    def cancelled(self) -> list[BookingRecord]:
        """Bookings that gave their room back, in start order."""
        return sorted(
            (record for record in self.bookings.values() if record.is_cancelled),
            key=lambda record: (record.start, record.booking_id),
        )

    def promoted(self) -> list[BookingRecord]:
        """Bookings that came off a waitlist, in start order."""
        return sorted(
            (record for record in self.bookings.values() if record.from_waitlist),
            key=lambda record: (record.start, record.booking_id),
        )

    def room_ids(self) -> list[str]:
        """Every room the log mentions, in id order."""
        return sorted({record.room_id for record in self.bookings.values()})

    def count_of(self, event_type: str) -> int:
        """How many lines of that event type the log held."""
        return self.event_counts.get(event_type, 0)

    def __len__(self) -> int:
        return len(self.bookings)


def parse_instant(text: str, *, field_name: str = "instant", line_number: int | None = None) -> datetime:
    """Parses an ISO-8601 instant in UTC, for example 2026-10-20T10:00:00Z.

    The platform always writes UTC with a trailing Z. An offset other than zero
    is refused rather than silently converted, because a report that mixes zones
    is worse than one that stops.
    """
    if not isinstance(text, str):
        raise EventLogError(f"{field_name} must be a string, got {type(text).__name__}", line_number)
    candidate = text.strip()
    if candidate.endswith("Z"):
        candidate = candidate[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(candidate)
    except ValueError as failure:
        raise EventLogError(f"{field_name} is not an ISO-8601 instant: {text!r}", line_number) from failure
    if parsed.tzinfo is None:
        raise EventLogError(f"{field_name} has no time zone: {text!r}", line_number)
    if parsed.utcoffset() != timezone.utc.utcoffset(None):
        raise EventLogError(f"{field_name} must be UTC: {text!r}", line_number)
    return parsed.astimezone(timezone.utc)


def event_from_mapping(data: Mapping[str, object], *, line_number: int | None = None) -> Event:
    """Turns one parsed JSON object into an Event, checking every field."""
    for name in _REQUIRED_FIELDS:
        if name not in data:
            raise EventLogError(f"missing field {name!r}", line_number)

    event_type = data["event"]
    if event_type not in KNOWN_EVENT_TYPES:
        raise EventLogError(f"unknown event type {event_type!r}", line_number)

    attendees = data["attendees"]
    if not isinstance(attendees, int) or isinstance(attendees, bool) or attendees <= 0:
        raise EventLogError(f"attendees must be a positive whole number, got {attendees!r}", line_number)

    start = parse_instant(data["start"], field_name="start", line_number=line_number)
    end = parse_instant(data["end"], field_name="end", line_number=line_number)
    if end <= start:
        raise EventLogError(f"end {data['end']!r} is not after start {data['start']!r}", line_number)

    sequence = data.get("waitlistSequence")
    if sequence is not None and (not isinstance(sequence, int) or isinstance(sequence, bool)):
        raise EventLogError(f"waitlistSequence must be a whole number, got {sequence!r}", line_number)

    return Event(
        event_type=event_type,
        occurred_at=parse_instant(data["occurredAt"], field_name="occurredAt", line_number=line_number),
        booking_id=_required_text(data, "bookingId", line_number),
        room_id=_required_text(data, "roomId", line_number),
        user_id=_required_text(data, "userId", line_number),
        start=start,
        end=end,
        attendees=attendees,
        waitlist_entry_id=data.get("waitlistEntryId"),
        waitlist_sequence=sequence,
    )


def parse_line(line: str, *, line_number: int | None = None) -> Event | None:
    """Parses one line. A blank line yields nothing rather than an error."""
    text = line.strip()
    if not text:
        return None
    try:
        data = json.loads(text)
    except json.JSONDecodeError as failure:
        raise EventLogError(f"not valid JSON: {failure.msg}", line_number) from failure
    if not isinstance(data, dict):
        raise EventLogError(f"expected a JSON object, got {type(data).__name__}", line_number)
    return event_from_mapping(data, line_number=line_number)


def iter_events(lines: Iterable[str]) -> Iterator[Event]:
    """Parses an iterable of lines, numbering them from one for error messages."""
    for line_number, line in enumerate(lines, start=1):
        event = parse_line(line, line_number=line_number)
        if event is not None:
            yield event


def load_events(path: str | Path) -> list[Event]:
    """Reads a whole events.jsonl file, in the order the platform wrote it."""
    location = Path(path)
    if not location.exists():
        raise EventLogError(f"no event log at {location}")
    with location.open(encoding="utf-8") as handle:
        return list(iter_events(handle))


def build_ledger(events: Sequence[Event]) -> Ledger:
    """Folds events into one record per booking.

    Creation and promotion both put a booking on a room's calendar, so both open
    a record; promotion is remembered so a report can tell the two apart.
    A modification restates the interval and the party size of the record it
    names, so a booking that was changed is counted once, at the length it holds
    now rather than at both lengths. Cancellation closes the record it names. A
    cancellation for a booking the log never opened is collected rather than
    dropped, because it usually means the export started mid-history; a
    modification of a booking the log never opened is skipped for the same
    reason, with nothing to restate.
    """
    ledger = Ledger()
    for event in events:
        ledger.event_counts[event.event_type] = ledger.event_counts.get(event.event_type, 0) + 1

        if event.puts_booking_on_calendar:
            ledger.bookings[event.booking_id] = BookingRecord(
                booking_id=event.booking_id,
                room_id=event.room_id,
                user_id=event.user_id,
                start=event.start,
                end=event.end,
                attendees=event.attendees,
                created_at=event.occurred_at,
                from_waitlist=event.came_from_waitlist,
                waitlist_entry_id=event.waitlist_entry_id,
            )
        elif event.changes_a_booking:
            record = ledger.bookings.get(event.booking_id)
            if record is not None:
                record.start = event.start
                record.end = event.end
                record.attendees = event.attendees
        elif event.event_type == BOOKING_CANCELLED:
            record = ledger.bookings.get(event.booking_id)
            if record is None:
                ledger.cancellations_without_a_booking.append(event.booking_id)
            else:
                record.cancelled_at = event.occurred_at

    return ledger


def load_ledger(path: str | Path) -> Ledger:
    """Reads an event log and folds it in one step."""
    return build_ledger(load_events(path))


def _required_text(data: Mapping[str, object], name: str, line_number: int | None) -> str:
    value = data[name]
    if not isinstance(value, str) or not value.strip():
        raise EventLogError(f"{name} must be a non-empty string, got {value!r}", line_number)
    return value
