"""The utilization report: how much of the bookable day each room sold.

Bookings that were cancelled do not count toward booked minutes, but they are
counted separately so an operator can see churn. Promotions count as bookings,
because a promoted waitlist entry holds the room exactly like any other booking.

The platform keeps every booking inside one UTC day and inside opening hours, so
this report can attribute a booking wholly to the day it starts on. No interval
arithmetic is needed here.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from typing import Sequence

from .events import BookingRecord, Ledger
from .windows import DateWindow

#: Minutes a room is bookable each day under the platform's default policy
#: (06:00 to 22:00 UTC).
DEFAULT_OPEN_MINUTES_PER_DAY = 16 * 60


@dataclass(frozen=True)
class RoomUtilization:
    """What one room sold over the window."""

    room_id: str
    booked_minutes: int
    booking_count: int
    cancelled_count: int
    promoted_count: int
    attendee_total: int
    occupancy_rate: float

    @property
    def mean_duration_minutes(self) -> float:
        """Average length of a held booking, or zero when there were none."""
        if self.booking_count == 0:
            return 0.0
        return self.booked_minutes / self.booking_count

    @property
    def mean_party_size(self) -> float:
        """Average attendee count over held bookings, or zero when there were none."""
        if self.booking_count == 0:
            return 0.0
        return self.attendee_total / self.booking_count

    @property
    def cancellation_rate(self) -> float:
        """Share of this room's bookings that were cancelled, between 0 and 1."""
        total = self.booking_count + self.cancelled_count
        if total == 0:
            return 0.0
        return self.cancelled_count / total


@dataclass(frozen=True)
class DayUtilization:
    """What every room together sold on one day."""

    day: date
    booked_minutes: int
    booking_count: int
    room_count: int

    @property
    def mean_minutes_per_room(self) -> float:
        """Booked minutes spread over the rooms that sold anything that day."""
        if self.room_count == 0:
            return 0.0
        return self.booked_minutes / self.room_count


@dataclass(frozen=True)
class UtilizationReport:
    """Room and day breakdowns over one window."""

    window: DateWindow
    open_minutes_per_day: int
    rooms: tuple[RoomUtilization, ...]
    days: tuple[DayUtilization, ...]
    start_hour_histogram: dict[int, int] = field(default_factory=dict)
    cancellations_without_a_booking: int = 0

    @property
    def total_booked_minutes(self) -> int:
        """Booked minutes across every room in the window."""
        return sum(room.booked_minutes for room in self.rooms)

    @property
    def total_bookings(self) -> int:
        """Held bookings across every room in the window."""
        return sum(room.booking_count for room in self.rooms)

    @property
    def total_cancellations(self) -> int:
        """Cancelled bookings across every room in the window."""
        return sum(room.cancelled_count for room in self.rooms)

    @property
    def total_promotions(self) -> int:
        """Held bookings that came off a waitlist."""
        return sum(room.promoted_count for room in self.rooms)

    @property
    def capacity_minutes(self) -> int:
        """Bookable minutes on offer: rooms times open minutes times days."""
        return len(self.rooms) * self.open_minutes_per_day * self.window.day_count

    @property
    def overall_occupancy(self) -> float:
        """Share of the bookable minutes that were sold, between 0 and 1."""
        if self.capacity_minutes == 0:
            return 0.0
        return self.total_booked_minutes / self.capacity_minutes

    @property
    def busiest_start_hour(self) -> int | None:
        """The UTC hour most bookings start in, or None when there are none."""
        if not self.start_hour_histogram:
            return None
        return max(sorted(self.start_hour_histogram), key=lambda hour: self.start_hour_histogram[hour])

    def room(self, room_id: str) -> RoomUtilization | None:
        """The row for one room, or None when the room sold nothing."""
        for row in self.rooms:
            if row.room_id == room_id:
                return row
        return None

    def day(self, when: date) -> DayUtilization | None:
        """The row for one day, or None when that day is outside the window."""
        for row in self.days:
            if row.day == when:
                return row
        return None


def build_utilization(
    ledger: Ledger,
    window: DateWindow,
    open_minutes_per_day: int = DEFAULT_OPEN_MINUTES_PER_DAY,
) -> UtilizationReport:
    """Builds the utilization report for one window.

    Rooms come back busiest first, with ties broken by room id so the output is
    stable. Every room the log mentions in the window gets a row, even one whose
    bookings were all cancelled, because a room with nothing but churn is worth
    seeing.
    """
    if open_minutes_per_day <= 0:
        raise ValueError(f"open minutes per day must be positive, got {open_minutes_per_day}")

    held = _in_window(ledger.held(), window)
    cancelled = _in_window(ledger.cancelled(), window)

    room_ids = sorted({record.room_id for record in held} | {record.room_id for record in cancelled})
    rooms = tuple(
        _room_row(room_id, held, cancelled, window, open_minutes_per_day) for room_id in room_ids
    )
    rooms = tuple(sorted(rooms, key=lambda row: (-row.booked_minutes, row.room_id)))

    days = tuple(_day_row(day, held) for day in window.days())

    histogram: dict[int, int] = {}
    for record in held:
        histogram[record.start_hour] = histogram.get(record.start_hour, 0) + 1

    return UtilizationReport(
        window=window,
        open_minutes_per_day=open_minutes_per_day,
        rooms=rooms,
        days=days,
        start_hour_histogram=histogram,
        cancellations_without_a_booking=len(ledger.cancellations_without_a_booking),
    )


def _in_window(records: Sequence[BookingRecord], window: DateWindow) -> list[BookingRecord]:
    return [record for record in records if window.contains(record.start)]


def _room_row(
    room_id: str,
    held: Sequence[BookingRecord],
    cancelled: Sequence[BookingRecord],
    window: DateWindow,
    open_minutes_per_day: int,
) -> RoomUtilization:
    mine = [record for record in held if record.room_id == room_id]
    booked_minutes = sum(record.duration_minutes for record in mine)
    capacity = open_minutes_per_day * window.day_count
    return RoomUtilization(
        room_id=room_id,
        booked_minutes=booked_minutes,
        booking_count=len(mine),
        cancelled_count=len([record for record in cancelled if record.room_id == room_id]),
        promoted_count=len([record for record in mine if record.from_waitlist]),
        attendee_total=sum(record.attendees for record in mine),
        occupancy_rate=min(1.0, booked_minutes / capacity) if capacity else 0.0,
    )


def _day_row(day: date, held: Sequence[BookingRecord]) -> DayUtilization:
    mine = [record for record in held if record.start_date == day]
    return DayUtilization(
        day=day,
        booked_minutes=sum(record.duration_minutes for record in mine),
        booking_count=len(mine),
        room_count=len({record.room_id for record in mine}),
    )
