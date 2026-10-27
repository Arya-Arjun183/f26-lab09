"""The date range a report covers."""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest

from campusreserve_analytics.windows import DateWindow, WindowError, parse_date


class TestParseDate:
    def test_reads_an_iso_date(self):
        assert parse_date("2026-10-20") == date(2026, 10, 20)

    def test_tolerates_surrounding_whitespace(self):
        assert parse_date("  2026-10-20 ") == date(2026, 10, 20)

    def test_refuses_anything_else(self):
        with pytest.raises(WindowError, match="not a YYYY-MM-DD date"):
            parse_date("20/10/2026")

    def test_names_the_field_it_was_reading(self):
        with pytest.raises(WindowError, match="from is not a YYYY-MM-DD date"):
            parse_date("nope", field_name="from")


class TestDateWindow:
    def test_counts_both_ends(self):
        window = DateWindow.from_texts("2026-10-19", "2026-10-25")
        assert window.day_count == 7
        assert list(window.days())[0] == date(2026, 10, 19)
        assert list(window.days())[-1] == date(2026, 10, 25)

    def test_a_single_day_window_counts_one_day(self):
        window = DateWindow.single_day(date(2026, 10, 20))
        assert window.day_count == 1
        assert list(window.days()) == [date(2026, 10, 20)]

    def test_refuses_a_backwards_range(self):
        with pytest.raises(WindowError, match="ends before it starts"):
            DateWindow.from_texts("2026-10-25", "2026-10-19")

    def test_contains_covers_both_ends(self):
        window = DateWindow.from_texts("2026-10-19", "2026-10-21")
        assert window.contains(date(2026, 10, 19))
        assert window.contains(date(2026, 10, 21))
        assert not window.contains(date(2026, 10, 18))
        assert not window.contains(date(2026, 10, 22))

    def test_contains_accepts_an_instant(self):
        window = DateWindow.single_day(date(2026, 10, 20))
        assert window.contains(datetime(2026, 10, 20, 23, 59, tzinfo=timezone.utc))
        assert not window.contains(datetime(2026, 10, 21, 0, 0, tzinfo=timezone.utc))

    def test_spans_the_moments_it_is_given(self):
        window = DateWindow.spanning([
            datetime(2026, 10, 22, 9, 0, tzinfo=timezone.utc),
            datetime(2026, 10, 19, 9, 0, tzinfo=timezone.utc),
            datetime(2026, 10, 20, 9, 0, tzinfo=timezone.utc),
        ])
        assert window.first == date(2026, 10, 19)
        assert window.last == date(2026, 10, 22)

    def test_spanning_nothing_is_refused(self):
        with pytest.raises(WindowError, match="from nothing"):
            DateWindow.spanning([])

    def test_labels_read_naturally(self):
        assert DateWindow.from_texts("2026-10-19", "2026-10-25").label() == "2026-10-19 to 2026-10-25"
        assert str(DateWindow.single_day(date(2026, 10, 20))) == "2026-10-20"

    def test_filters_records_by_start_date(self, small_ledger):
        window = DateWindow.single_day(date(2026, 10, 21))
        kept = window.filter(small_ledger.held())
        assert [record.booking_id for record in kept] == ["bk-5"]
