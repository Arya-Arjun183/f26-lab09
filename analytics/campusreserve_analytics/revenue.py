"""The revenue report: what the sold time was worth.

The event log carries no prices, so this report values booked minutes with a
rate table the operator supplies. It is an estimate of the value of the time
sold, not a copy of what the platform invoiced. Invoices live in the billing
module and are not exported to the event log.

Money is counted in whole cents. Nothing here uses a float for an amount.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Sequence

from .events import BookingRecord, Ledger
from .windows import DateWindow

#: Rate applied to a room the table does not name.
DEFAULT_HOURLY_CENTS = 1200

#: UTC hours treated as peak when the table does not say otherwise.
DEFAULT_PEAK_HOURS = (9, 17)


class RateError(ValueError):
    """A rate table could not be built."""


@dataclass(frozen=True)
class RateTable:
    """What an hour in a room is worth, plus the two adjustments.

    A booking that starts inside the peak window carries the peak surcharge. A
    booking with at least ``large_group_threshold`` attendees carries the group
    surcharge. Both are percentages of the base amount, applied to the base and
    not to each other.
    """

    default_hourly_cents: int = DEFAULT_HOURLY_CENTS
    per_room_hourly_cents: dict[str, int] = field(default_factory=dict)
    peak_first_hour: int = DEFAULT_PEAK_HOURS[0]
    peak_last_hour: int = DEFAULT_PEAK_HOURS[1]
    peak_surcharge_percent: int = 25
    large_group_threshold: int = 12
    large_group_surcharge_percent: int = 15

    def __post_init__(self) -> None:
        if self.default_hourly_cents < 0:
            raise RateError(f"default hourly rate must not be negative, got {self.default_hourly_cents}")
        for room_id, cents in self.per_room_hourly_cents.items():
            if cents < 0:
                raise RateError(f"hourly rate for {room_id} must not be negative, got {cents}")
        if not 0 <= self.peak_first_hour <= 23 or not 0 <= self.peak_last_hour <= 23:
            raise RateError(
                f"peak hours must be between 0 and 23, got {self.peak_first_hour} to {self.peak_last_hour}"
            )
        if self.peak_last_hour < self.peak_first_hour:
            raise RateError(
                f"peak window ends before it starts: {self.peak_first_hour} to {self.peak_last_hour}"
            )
        if self.peak_surcharge_percent < 0 or self.large_group_surcharge_percent < 0:
            raise RateError("surcharge percentages must not be negative")
        if self.large_group_threshold <= 0:
            raise RateError(f"large group threshold must be positive, got {self.large_group_threshold}")

    @classmethod
    def flat(cls, hourly_cents: int) -> "RateTable":
        """One rate for every room, with no surcharges."""
        return cls(
            default_hourly_cents=hourly_cents,
            peak_surcharge_percent=0,
            large_group_surcharge_percent=0,
        )

    def hourly_cents_for(self, room_id: str) -> int:
        """The rate for a room, falling back to the default."""
        return self.per_room_hourly_cents.get(room_id, self.default_hourly_cents)

    def with_room_rate(self, room_id: str, hourly_cents: int) -> "RateTable":
        """A copy that prices one room differently."""
        rates = dict(self.per_room_hourly_cents)
        rates[room_id] = hourly_cents
        return RateTable(
            default_hourly_cents=self.default_hourly_cents,
            per_room_hourly_cents=rates,
            peak_first_hour=self.peak_first_hour,
            peak_last_hour=self.peak_last_hour,
            peak_surcharge_percent=self.peak_surcharge_percent,
            large_group_threshold=self.large_group_threshold,
            large_group_surcharge_percent=self.large_group_surcharge_percent,
        )

    def is_peak(self, start_hour: int) -> bool:
        """Does a booking starting in this UTC hour carry the peak surcharge?"""
        return self.peak_first_hour <= start_hour <= self.peak_last_hour

    def is_large_group(self, attendees: int) -> bool:
        """Does a party this size carry the group surcharge?"""
        return attendees >= self.large_group_threshold


@dataclass(frozen=True)
class BookingValue:
    """What one booking was worth, broken into its parts."""

    booking_id: str
    room_id: str
    minutes: int
    base_cents: int
    peak_cents: int
    group_cents: int

    @property
    def total_cents(self) -> int:
        """Base plus both surcharges."""
        return self.base_cents + self.peak_cents + self.group_cents


@dataclass(frozen=True)
class RoomRevenue:
    """What one room earned, and what its cancellations gave up."""

    room_id: str
    booking_count: int
    billable_minutes: int
    base_cents: int
    peak_surcharge_cents: int
    group_surcharge_cents: int
    forgone_cents: int

    @property
    def net_cents(self) -> int:
        """What the held bookings are worth."""
        return self.base_cents + self.peak_surcharge_cents + self.group_surcharge_cents

    @property
    def mean_cents_per_booking(self) -> float:
        """Average value of a held booking, or zero when there were none."""
        if self.booking_count == 0:
            return 0.0
        return self.net_cents / self.booking_count


@dataclass(frozen=True)
class RevenueReport:
    """Per-room values over one window, plus the totals."""

    window: DateWindow
    rates: RateTable
    rooms: tuple[RoomRevenue, ...]

    @property
    def net_cents(self) -> int:
        """What every held booking in the window is worth."""
        return sum(room.net_cents for room in self.rooms)

    @property
    def forgone_cents(self) -> int:
        """What the cancelled bookings in the window would have been worth."""
        return sum(room.forgone_cents for room in self.rooms)

    @property
    def surcharge_cents(self) -> int:
        """The part of the net value that came from surcharges."""
        return sum(room.peak_surcharge_cents + room.group_surcharge_cents for room in self.rooms)

    @property
    def billable_minutes(self) -> int:
        """Held minutes across every room in the window."""
        return sum(room.billable_minutes for room in self.rooms)

    @property
    def booking_count(self) -> int:
        """Held bookings across every room in the window."""
        return sum(room.booking_count for room in self.rooms)

    @property
    def realization_rate(self) -> float:
        """Net value over net plus forgone, between 0 and 1."""
        offered = self.net_cents + self.forgone_cents
        if offered == 0:
            return 0.0
        return self.net_cents / offered

    def room(self, room_id: str) -> RoomRevenue | None:
        """The row for one room, or None when the room earned nothing."""
        for row in self.rooms:
            if row.room_id == room_id:
                return row
        return None


def value_of(record: BookingRecord, rates: RateTable) -> BookingValue:
    """Values one booking: base by the minute, then the two surcharges."""
    hourly = rates.hourly_cents_for(record.room_id)
    base = round_half_up(hourly * record.duration_minutes, 60)
    peak = percent_of(base, rates.peak_surcharge_percent) if rates.is_peak(record.start_hour) else 0
    group = (
        percent_of(base, rates.large_group_surcharge_percent)
        if rates.is_large_group(record.attendees)
        else 0
    )
    return BookingValue(
        booking_id=record.booking_id,
        room_id=record.room_id,
        minutes=record.duration_minutes,
        base_cents=base,
        peak_cents=peak,
        group_cents=group,
    )


def build_revenue(ledger: Ledger, window: DateWindow, rates: RateTable | None = None) -> RevenueReport:
    """Builds the revenue report for one window.

    Rooms come back by net value, highest first, with ties broken by room id.
    """
    table = rates or RateTable()
    held = [record for record in ledger.held() if window.contains(record.start)]
    cancelled = [record for record in ledger.cancelled() if window.contains(record.start)]

    room_ids = sorted({record.room_id for record in held} | {record.room_id for record in cancelled})
    rooms = tuple(_room_row(room_id, held, cancelled, table) for room_id in room_ids)
    rooms = tuple(sorted(rooms, key=lambda row: (-row.net_cents, row.room_id)))
    return RevenueReport(window=window, rates=table, rooms=rooms)


def percent_of(amount_cents: int, percent: int) -> int:
    """A percentage of an amount in cents, rounded half up."""
    return round_half_up(amount_cents * percent, 100)


def round_half_up(numerator: int, denominator: int) -> int:
    """Integer division rounding halves away from zero."""
    if denominator <= 0:
        raise RateError(f"denominator must be positive, got {denominator}")
    sign = -1 if numerator < 0 else 1
    return sign * ((abs(numerator) * 2 + denominator) // (denominator * 2))


def cents_to_text(cents: int) -> str:
    """Renders whole cents as an amount, for example -1250 becomes -12.50."""
    sign = "-" if cents < 0 else ""
    whole, remainder = divmod(abs(cents), 100)
    return f"{sign}{whole}.{remainder:02d}"


def _room_row(
    room_id: str,
    held: Sequence[BookingRecord],
    cancelled: Sequence[BookingRecord],
    rates: RateTable,
) -> RoomRevenue:
    mine = [value_of(record, rates) for record in held if record.room_id == room_id]
    lost = [value_of(record, rates) for record in cancelled if record.room_id == room_id]
    return RoomRevenue(
        room_id=room_id,
        booking_count=len(mine),
        billable_minutes=sum(value.minutes for value in mine),
        base_cents=sum(value.base_cents for value in mine),
        peak_surcharge_cents=sum(value.peak_cents for value in mine),
        group_surcharge_cents=sum(value.group_cents for value in mine),
        forgone_cents=sum(value.total_cents for value in lost),
    )
