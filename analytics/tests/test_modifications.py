"""Bookings that changed after they were made."""

from __future__ import annotations

import pytest

from campusreserve_analytics.events import (
    BOOKING_CANCELLED,
    BOOKING_MODIFIED,
    KNOWN_EVENT_TYPES,
    EventLogError,
    parse_line,
)
from campusreserve_analytics.utilization import build_utilization
from campusreserve_analytics.windows import DateWindow

from .conftest import ledger_from_lines, make_line

ONE_DAY = DateWindow.from_texts("2026-10-20", "2026-10-20")


def modified_line(
    booking_id: str = "bk-1",
    start: str = "2026-10-20T13:00:00Z",
    end: str = "2026-10-20T14:00:00Z",
    attendees: int = 4,
    **extra: object,
) -> str:
    """A modification line, with the fields the platform adds to one."""
    return make_line(
        event=BOOKING_MODIFIED,
        booking_id=booking_id,
        start=start,
        end=end,
        attendees=attendees,
        previousStart="2026-10-20T10:00:00Z",
        previousEnd="2026-10-20T11:00:00Z",
        previousAttendees=4,
        **extra,
    )


class TestReadingTheLine:
    def test_the_platform_declares_the_type(self):
        assert BOOKING_MODIFIED in KNOWN_EVENT_TYPES

    def test_reads_a_modification(self):
        event = parse_line(modified_line())
        assert event.event_type == BOOKING_MODIFIED
        assert event.booking_id == "bk-1"
        assert event.duration_minutes == 60
        assert event.changes_a_booking
        assert not event.puts_booking_on_calendar

    def test_a_modification_still_needs_the_core_fields(self):
        with pytest.raises(EventLogError, match="end .* is not after start"):
            parse_line(modified_line(start="2026-10-20T13:00:00Z", end="2026-10-20T13:00:00Z"))


class TestFolding:
    def test_a_shortened_booking_counts_at_its_new_length(self):
        ledger = ledger_from_lines(
            make_line(),
            modified_line(start="2026-10-20T10:00:00Z", end="2026-10-20T10:30:00Z"),
        )
        assert len(ledger) == 1
        assert ledger.bookings["bk-1"].duration_minutes == 30

    def test_a_lengthened_booking_counts_at_its_new_length(self):
        ledger = ledger_from_lines(
            make_line(),
            modified_line(start="2026-10-20T10:00:00Z", end="2026-10-20T12:00:00Z"),
        )
        assert ledger.bookings["bk-1"].duration_minutes == 120

    def test_a_moved_booking_keeps_one_record(self):
        ledger = ledger_from_lines(make_line(), modified_line())
        assert len(ledger) == 1
        record = ledger.bookings["bk-1"]
        assert record.start_hour == 13
        assert record.holds_room

    def test_a_party_size_change_reaches_the_record(self):
        ledger = ledger_from_lines(
            make_line(attendees=4),
            modified_line(start="2026-10-20T10:00:00Z", end="2026-10-20T11:00:00Z", attendees=9),
        )
        assert ledger.bookings["bk-1"].attendees == 9

    def test_a_promoted_booking_can_be_modified_too(self):
        ledger = ledger_from_lines(
            make_line(event="waitlist-promoted", waitlistEntryId="wl-1", waitlistSequence=1),
            modified_line(),
        )
        record = ledger.bookings["bk-1"]
        assert record.from_waitlist
        assert record.start_hour == 13

    def test_a_modified_booking_can_still_be_cancelled(self):
        ledger = ledger_from_lines(
            make_line(),
            modified_line(),
            make_line(event=BOOKING_CANCELLED),
        )
        assert ledger.bookings["bk-1"].is_cancelled
        assert ledger.held() == []

    def test_a_modification_of_a_booking_the_log_never_opened_is_skipped(self):
        ledger = ledger_from_lines(modified_line(booking_id="bk-99"))
        assert len(ledger) == 0
        assert ledger.count_of(BOOKING_MODIFIED) == 1

    def test_modifications_are_counted_like_every_other_line(self):
        ledger = ledger_from_lines(make_line(), modified_line(), modified_line(attendees=2))
        assert ledger.count_of(BOOKING_MODIFIED) == 2


class TestUtilization:
    def test_the_report_uses_the_current_length(self):
        ledger = ledger_from_lines(
            make_line(),
            modified_line(start="2026-10-20T10:00:00Z", end="2026-10-20T10:30:00Z"),
        )
        report = build_utilization(ledger, ONE_DAY)
        assert report.room("r-1").booked_minutes == 30
        assert report.room("r-1").booking_count == 1
        assert report.total_booked_minutes == 30

    def test_a_moved_booking_lands_in_its_new_hour(self):
        ledger = ledger_from_lines(make_line(), modified_line())
        report = build_utilization(ledger, ONE_DAY)
        assert report.busiest_start_hour == 13
        assert report.total_bookings == 1
