"""Turning a report into something to read or into CSV.

Text output is aligned columns meant for a terminal. CSV output is meant for a
spreadsheet and has one header row and no blank lines.
"""

from __future__ import annotations

import csv
import io
from typing import Sequence

from .revenue import RevenueReport, cents_to_text
from .utilization import UtilizationReport


def format_table(headers: Sequence[str], rows: Sequence[Sequence[str]]) -> str:
    """Aligns rows under headers, padding every column to its widest cell.

    Numeric-looking columns are right aligned so digits line up; everything else
    is left aligned.
    """
    if not headers:
        raise ValueError("a table needs at least one column")
    widths = [len(header) for header in headers]
    for row in rows:
        if len(row) != len(headers):
            raise ValueError(f"row has {len(row)} cells but there are {len(headers)} columns")
        for index, cell in enumerate(row):
            widths[index] = max(widths[index], len(cell))

    right_aligned = [_is_numeric_column(headers, rows, index) for index in range(len(headers))]

    lines = [_render_row(headers, widths, right_aligned)]
    lines.append("  ".join("-" * width for width in widths))
    for row in rows:
        lines.append(_render_row(row, widths, right_aligned))
    return "\n".join(lines)


def render_utilization(report: UtilizationReport) -> str:
    """The utilization report as aligned text."""
    lines = [
        f"Utilization for {report.window.label()}",
        f"Rooms {len(report.rooms)}   days {report.window.day_count}"
        f"   open minutes per day {report.open_minutes_per_day}",
        "",
    ]

    room_rows = [
        [
            room.room_id,
            str(room.booking_count),
            str(room.booked_minutes),
            f"{room.mean_duration_minutes:.0f}",
            f"{room.mean_party_size:.1f}",
            str(room.promoted_count),
            str(room.cancelled_count),
            f"{room.occupancy_rate * 100:.1f}%",
        ]
        for room in report.rooms
    ]
    lines.append(
        format_table(
            ["room", "bookings", "minutes", "mean min", "mean party", "promoted", "cancelled", "occupancy"],
            room_rows,
        )
    )

    lines.append("")
    day_rows = [
        [day.day.isoformat(), str(day.booking_count), str(day.booked_minutes), str(day.room_count)]
        for day in report.days
    ]
    lines.append(format_table(["day", "bookings", "minutes", "rooms"], day_rows))

    lines.append("")
    lines.append(
        f"Total {report.total_bookings} bookings over {report.total_booked_minutes} minutes"
        f", {report.total_promotions} from the waitlist"
        f", {report.total_cancellations} cancelled"
    )
    lines.append(f"Overall occupancy {report.overall_occupancy * 100:.1f}%")
    if report.busiest_start_hour is not None:
        lines.append(f"Busiest start hour {report.busiest_start_hour:02d}:00 UTC")
    if report.cancellations_without_a_booking:
        lines.append(
            f"Note: {report.cancellations_without_a_booking} cancellations name a booking"
            " the log never opened"
        )
    return "\n".join(lines)


def render_revenue(report: RevenueReport) -> str:
    """The revenue report as aligned text."""
    lines = [
        f"Revenue for {report.window.label()}",
        f"Default rate {cents_to_text(report.rates.default_hourly_cents)} an hour"
        f"   peak {report.rates.peak_first_hour:02d}:00 to {report.rates.peak_last_hour:02d}:59 UTC"
        f" at {report.rates.peak_surcharge_percent}%"
        f"   groups of {report.rates.large_group_threshold} or more"
        f" at {report.rates.large_group_surcharge_percent}%",
        "",
    ]

    rows = [
        [
            room.room_id,
            str(room.booking_count),
            str(room.billable_minutes),
            cents_to_text(room.base_cents),
            cents_to_text(room.peak_surcharge_cents),
            cents_to_text(room.group_surcharge_cents),
            cents_to_text(room.net_cents),
            cents_to_text(room.forgone_cents),
        ]
        for room in report.rooms
    ]
    lines.append(
        format_table(
            ["room", "bookings", "minutes", "base", "peak", "group", "net", "forgone"],
            rows,
        )
    )

    lines.append("")
    lines.append(
        f"Net {cents_to_text(report.net_cents)} over {report.booking_count} bookings"
        f" ({cents_to_text(report.surcharge_cents)} of it surcharges)"
    )
    lines.append(
        f"Forgone to cancellations {cents_to_text(report.forgone_cents)}"
        f", realization {report.realization_rate * 100:.1f}%"
    )
    return "\n".join(lines)


def utilization_csv(report: UtilizationReport) -> str:
    """The utilization report's per-room rows as CSV."""
    return _to_csv(
        ["room", "bookings", "minutes", "mean_minutes", "promoted", "cancelled", "occupancy"],
        [
            [
                room.room_id,
                room.booking_count,
                room.booked_minutes,
                f"{room.mean_duration_minutes:.1f}",
                room.promoted_count,
                room.cancelled_count,
                f"{room.occupancy_rate:.4f}",
            ]
            for room in report.rooms
        ],
    )


def revenue_csv(report: RevenueReport) -> str:
    """The revenue report's per-room rows as CSV."""
    return _to_csv(
        ["room", "bookings", "minutes", "base_cents", "peak_cents", "group_cents", "net_cents",
         "forgone_cents"],
        [
            [
                room.room_id,
                room.booking_count,
                room.billable_minutes,
                room.base_cents,
                room.peak_surcharge_cents,
                room.group_surcharge_cents,
                room.net_cents,
                room.forgone_cents,
            ]
            for room in report.rooms
        ],
    )


def _to_csv(headers: Sequence[str], rows: Sequence[Sequence[object]]) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(headers)
    writer.writerows(rows)
    return buffer.getvalue().rstrip("\n")


def _render_row(cells: Sequence[str], widths: Sequence[int], right_aligned: Sequence[bool]) -> str:
    rendered = [
        cell.rjust(widths[index]) if right_aligned[index] else cell.ljust(widths[index])
        for index, cell in enumerate(cells)
    ]
    return "  ".join(rendered).rstrip()


def _is_numeric_column(headers: Sequence[str], rows: Sequence[Sequence[str]], index: int) -> bool:
    if index == 0:
        return False
    cells = [row[index] for row in rows]
    if not cells:
        return False
    return all(_looks_numeric(cell) for cell in cells)


def _looks_numeric(cell: str) -> bool:
    stripped = cell.rstrip("%").lstrip("-")
    return bool(stripped) and stripped.replace(".", "", 1).isdigit()
