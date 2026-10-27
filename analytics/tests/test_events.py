"""Reading and folding the event log."""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest

from campusreserve_analytics.events import (
    BOOKING_CANCELLED,
    BOOKING_CREATED,
    KNOWN_EVENT_TYPES,
    WAITLIST_PROMOTED,
    EventLogError,
    build_ledger,
    iter_events,
    load_events,
    load_ledger,
    parse_instant,
    parse_line,
)

from .conftest import ledger_from_lines, make_line, minutes_later


class TestParseInstant:
    def test_reads_a_trailing_z(self):
        assert parse_instant("2026-10-20T10:00:00Z") == datetime(
            2026, 10, 20, 10, 0, tzinfo=timezone.utc
        )

    def test_reads_an_explicit_zero_offset(self):
        assert parse_instant("2026-10-20T10:00:00+00:00") == datetime(
            2026, 10, 20, 10, 0, tzinfo=timezone.utc
        )

    def test_refuses_a_non_utc_offset(self):
        with pytest.raises(EventLogError, match="must be UTC"):
            parse_instant("2026-10-20T10:00:00-04:00")

    def test_refuses_a_naive_timestamp(self):
        with pytest.raises(EventLogError, match="no time zone"):
            parse_instant("2026-10-20T10:00:00")

    def test_refuses_nonsense(self):
        with pytest.raises(EventLogError, match="not an ISO-8601 instant"):
            parse_instant("the twentieth")

    def test_names_the_line_in_the_message(self):
        with pytest.raises(EventLogError, match="line 7"):
            parse_instant("nope", line_number=7)


class TestParseLine:
    def test_reads_a_created_event(self):
        event = parse_line(make_line())
        assert event.event_type == BOOKING_CREATED
        assert event.booking_id == "bk-1"
        assert event.room_id == "r-1"
        assert event.attendees == 4
        assert event.duration_minutes == 60
        assert event.start_date == date(2026, 10, 20)
        assert event.start_hour == 10
        assert event.puts_booking_on_calendar
        assert not event.came_from_waitlist

    def test_reads_a_promotion_with_its_waitlist_fields(self):
        event = parse_line(
            make_line(event=WAITLIST_PROMOTED, waitlistEntryId="wl-4", waitlistSequence=4)
        )
        assert event.came_from_waitlist
        assert event.puts_booking_on_calendar
        assert event.waitlist_entry_id == "wl-4"
        assert event.waitlist_sequence == 4

    def test_a_cancellation_does_not_put_a_booking_on_the_calendar(self):
        assert not parse_line(make_line(event=BOOKING_CANCELLED)).puts_booking_on_calendar

    def test_a_blank_line_yields_nothing(self):
        assert parse_line("   ") is None
        assert parse_line("") is None

    def test_refuses_an_unknown_event_type(self):
        with pytest.raises(EventLogError, match="unknown event type"):
            parse_line(make_line(event="booking-invented"))

    def test_refuses_a_missing_field(self):
        line = make_line().replace(',"attendees":4', "")
        with pytest.raises(EventLogError, match="missing field 'attendees'"):
            parse_line(line)

    def test_refuses_bad_json(self):
        with pytest.raises(EventLogError, match="not valid JSON"):
            parse_line("{oops")

    def test_refuses_a_json_array(self):
        with pytest.raises(EventLogError, match="expected a JSON object"):
            parse_line("[1, 2, 3]")

    def test_refuses_a_non_positive_attendee_count(self):
        with pytest.raises(EventLogError, match="attendees must be a positive"):
            parse_line(make_line(attendees=0))

    def test_refuses_a_boolean_attendee_count(self):
        with pytest.raises(EventLogError, match="attendees must be a positive"):
            parse_line(make_line(attendees=True))

    def test_refuses_an_end_that_is_not_after_the_start(self):
        with pytest.raises(EventLogError, match="is not after start"):
            parse_line(make_line(end="2026-10-20T10:00:00Z"))

    def test_refuses_a_blank_identifier(self):
        with pytest.raises(EventLogError, match="bookingId must be a non-empty string"):
            parse_line(make_line(booking_id=" "))


class TestIterEvents:
    def test_numbers_lines_from_one(self):
        with pytest.raises(EventLogError, match="line 2"):
            list(iter_events([make_line(), "{broken"]))

    def test_skips_blank_lines_without_shifting_the_numbering(self):
        events = list(iter_events([make_line(), "", make_line(booking_id="bk-2")]))
        assert [event.booking_id for event in events] == ["bk-1", "bk-2"]


class TestLoadEvents:
    def test_reads_a_file_in_order(self, small_log):
        events = load_events(small_log)
        assert len(events) == 6
        assert [event.booking_id for event in events] == ["bk-1", "bk-2", "bk-3", "bk-2", "bk-4", "bk-5"]

    def test_reads_an_empty_file(self, fixture_dir):
        assert load_events(fixture_dir / "events-empty.jsonl") == []

    def test_refuses_a_missing_file(self, tmp_path):
        with pytest.raises(EventLogError, match="no event log at"):
            load_events(tmp_path / "nothing.jsonl")

    def test_reports_the_offending_line_number(self, fixture_dir):
        with pytest.raises(EventLogError, match="line 3"):
            load_events(fixture_dir / "events-malformed.jsonl")


class TestBuildLedger:
    def test_opens_a_record_for_each_creation(self, small_ledger):
        assert len(small_ledger) == 5
        assert set(small_ledger.bookings) == {"bk-1", "bk-2", "bk-3", "bk-4", "bk-5"}

    def test_a_cancellation_closes_the_record_it_names(self, small_ledger):
        assert small_ledger.bookings["bk-2"].is_cancelled
        assert not small_ledger.bookings["bk-2"].holds_room
        assert small_ledger.bookings["bk-1"].holds_room

    def test_a_promotion_opens_a_record_marked_as_promoted(self, small_ledger):
        promoted = small_ledger.bookings["bk-4"]
        assert promoted.from_waitlist
        assert promoted.holds_room
        assert promoted.waitlist_entry_id == "wl-1"

    def test_held_cancelled_and_promoted_come_back_in_start_order(self, small_ledger):
        assert [record.booking_id for record in small_ledger.held()] == ["bk-1", "bk-4", "bk-3", "bk-5"]
        assert [record.booking_id for record in small_ledger.cancelled()] == ["bk-2"]
        assert [record.booking_id for record in small_ledger.promoted()] == ["bk-4"]

    def test_counts_lines_by_type(self, small_ledger):
        assert small_ledger.count_of(BOOKING_CREATED) == 4
        assert small_ledger.count_of(BOOKING_CANCELLED) == 1
        assert small_ledger.count_of(WAITLIST_PROMOTED) == 1
        assert small_ledger.count_of("booking-invented") == 0

    def test_lists_the_rooms_it_saw(self, small_ledger):
        assert small_ledger.room_ids() == ["r-1", "r-2"]

    def test_records_the_duration_of_each_booking(self, small_ledger):
        assert small_ledger.bookings["bk-1"].duration_minutes == 60
        assert small_ledger.bookings["bk-3"].duration_minutes == 90
        assert small_ledger.bookings["bk-4"].duration_minutes == 120

    def test_a_cancellation_for_an_unseen_booking_is_collected(self):
        ledger = ledger_from_lines(make_line(event=BOOKING_CANCELLED, booking_id="bk-ghost"))
        assert ledger.cancellations_without_a_booking == ["bk-ghost"]
        assert len(ledger) == 0

    def test_a_cancellation_after_a_promotion_closes_the_promoted_booking(self):
        ledger = ledger_from_lines(
            make_line(event=WAITLIST_PROMOTED, booking_id="bk-9", waitlistEntryId="wl-1",
                      waitlistSequence=1),
            make_line(event=BOOKING_CANCELLED, booking_id="bk-9",
                      occurred_at=minutes_later("2026-10-19T09:00:00Z", 30)),
        )
        assert ledger.bookings["bk-9"].is_cancelled
        assert ledger.bookings["bk-9"].from_waitlist

    def test_an_empty_log_gives_an_empty_ledger(self, fixture_dir):
        ledger = load_ledger(fixture_dir / "events-empty.jsonl")
        assert len(ledger) == 0
        assert ledger.held() == []
        assert ledger.room_ids() == []

    def test_build_ledger_accepts_events_directly(self, small_events):
        assert len(build_ledger(small_events)) == 5


def test_the_known_event_types_cover_what_the_platform_writes():
    assert "booking-created" in KNOWN_EVENT_TYPES
    assert "booking-cancelled" in KNOWN_EVENT_TYPES
    assert "waitlist-promoted" in KNOWN_EVENT_TYPES
