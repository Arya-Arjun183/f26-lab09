"""What the sold time was worth."""

from __future__ import annotations

from datetime import date, timedelta

import pytest

from campusreserve_analytics.events import BookingRecord
from campusreserve_analytics.revenue import (
    RateError,
    RateTable,
    build_revenue,
    cents_to_text,
    percent_of,
    round_half_up,
    value_of,
)
from campusreserve_analytics.windows import DateWindow

from .conftest import instant, ledger_from_lines, make_line

TWO_DAYS = DateWindow.from_texts("2026-10-20", "2026-10-21")


def record(room_id="r-1", minutes=60, hour=10, attendees=4, booking_id="bk-1"):
    """One booking record, so a test can vary exactly one part of the pricing."""
    start = instant(f"2026-10-20T{hour:02d}:00:00Z")
    return BookingRecord(
        booking_id=booking_id,
        room_id=room_id,
        user_id="u-1",
        start=start,
        end=start + timedelta(minutes=minutes),
        attendees=attendees,
        created_at=instant("2026-10-19T09:00:00Z"),
    )


class TestArithmetic:
    def test_rounds_halves_away_from_zero(self):
        assert round_half_up(3, 2) == 2
        assert round_half_up(1, 2) == 1
        assert round_half_up(-3, 2) == -2
        assert round_half_up(4, 2) == 2

    def test_refuses_a_non_positive_denominator(self):
        with pytest.raises(RateError, match="denominator must be positive"):
            round_half_up(1, 0)

    def test_percentages_round_the_same_way(self):
        assert percent_of(1200, 25) == 300
        assert percent_of(1800, 15) == 270
        assert percent_of(10, 25) == 3
        assert percent_of(1000, 0) == 0

    def test_renders_cents_as_an_amount(self):
        assert cents_to_text(1250) == "12.50"
        assert cents_to_text(5) == "0.05"
        assert cents_to_text(0) == "0.00"
        assert cents_to_text(-1250) == "-12.50"


class TestRateTable:
    def test_falls_back_to_the_default_rate(self):
        table = RateTable(default_hourly_cents=1000, per_room_hourly_cents={"r-2": 2000})
        assert table.hourly_cents_for("r-1") == 1000
        assert table.hourly_cents_for("r-2") == 2000

    def test_copies_add_one_room_rate(self):
        table = RateTable().with_room_rate("r-9", 500)
        assert table.hourly_cents_for("r-9") == 500
        assert RateTable().hourly_cents_for("r-9") == RateTable().default_hourly_cents

    def test_a_flat_table_has_no_surcharges(self):
        table = RateTable.flat(900)
        assert table.hourly_cents_for("anything") == 900
        assert table.peak_surcharge_percent == 0
        assert table.large_group_surcharge_percent == 0

    def test_knows_its_peak_window_and_group_threshold(self):
        table = RateTable(peak_first_hour=9, peak_last_hour=17, large_group_threshold=12)
        assert table.is_peak(9)
        assert table.is_peak(17)
        assert not table.is_peak(8)
        assert not table.is_peak(18)
        assert table.is_large_group(12)
        assert not table.is_large_group(11)

    def test_refuses_a_negative_rate(self):
        with pytest.raises(RateError, match="must not be negative"):
            RateTable(default_hourly_cents=-1)
        with pytest.raises(RateError, match="must not be negative"):
            RateTable(per_room_hourly_cents={"r-1": -5})

    def test_refuses_an_impossible_peak_window(self):
        with pytest.raises(RateError, match="between 0 and 23"):
            RateTable(peak_first_hour=24)
        with pytest.raises(RateError, match="ends before it starts"):
            RateTable(peak_first_hour=17, peak_last_hour=9)

    def test_refuses_a_non_positive_group_threshold(self):
        with pytest.raises(RateError, match="threshold must be positive"):
            RateTable(large_group_threshold=0)


class TestValueOfOneBooking:
    def test_bills_the_base_by_the_minute(self):
        table = RateTable.flat(1200)
        assert value_of(record(minutes=60), table).base_cents == 1200
        assert value_of(record(minutes=90), table).base_cents == 1800
        assert value_of(record(minutes=30), table).base_cents == 600
        assert value_of(record(minutes=45), table).base_cents == 900

    def test_adds_the_peak_surcharge_when_it_starts_in_the_window(self):
        table = RateTable(default_hourly_cents=1200, peak_first_hour=9, peak_last_hour=17,
                          peak_surcharge_percent=25, large_group_surcharge_percent=0)
        assert value_of(record(hour=10), table).peak_cents == 300
        assert value_of(record(hour=18), table).peak_cents == 0

    def test_adds_the_group_surcharge_above_the_threshold(self):
        table = RateTable(default_hourly_cents=1200, peak_surcharge_percent=0,
                          large_group_threshold=12, large_group_surcharge_percent=15)
        assert value_of(record(attendees=12), table).group_cents == 180
        assert value_of(record(attendees=11), table).group_cents == 0

    def test_surcharges_apply_to_the_base_not_to_each_other(self):
        table = RateTable(default_hourly_cents=1200, peak_first_hour=9, peak_last_hour=17,
                          peak_surcharge_percent=25, large_group_threshold=12,
                          large_group_surcharge_percent=15)
        valued = value_of(record(hour=10, attendees=12), table)
        assert valued.base_cents == 1200
        assert valued.peak_cents == 300
        assert valued.group_cents == 180
        assert valued.total_cents == 1680


class TestReport:
    def test_values_each_room(self, small_ledger):
        report = build_revenue(small_ledger, TWO_DAYS)
        first = report.room("r-1")
        assert first.booking_count == 2
        assert first.billable_minutes == 180
        assert first.base_cents == 3600
        assert first.peak_surcharge_cents == 900
        assert first.group_surcharge_cents == 0
        assert first.net_cents == 4500

    def test_a_large_party_carries_the_group_surcharge(self, small_ledger):
        second = build_revenue(small_ledger, TWO_DAYS).room("r-2")
        assert second.group_surcharge_cents == 270
        assert second.net_cents == 4020

    def test_cancelled_bookings_land_in_forgone_not_in_net(self, small_ledger):
        report = build_revenue(small_ledger, TWO_DAYS)
        assert report.room("r-1").forgone_cents == 3000
        assert report.net_cents == 8520
        assert report.forgone_cents == 3000

    def test_totals_add_up(self, small_ledger):
        report = build_revenue(small_ledger, TWO_DAYS)
        assert report.booking_count == 4
        assert report.billable_minutes == 330
        assert report.surcharge_cents == 1920
        assert report.realization_rate == pytest.approx(8520 / 11520)

    def test_rooms_come_back_by_value(self, small_ledger):
        assert [room.room_id for room in build_revenue(small_ledger, TWO_DAYS).rooms] == ["r-1", "r-2"]

    def test_mean_value_per_booking(self, small_ledger):
        assert build_revenue(small_ledger, TWO_DAYS).room("r-1").mean_cents_per_booking == pytest.approx(2250)

    def test_a_room_with_no_held_bookings_reports_zero(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", room_id="r-5"),
            make_line(event="booking-cancelled", booking_id="bk-1", room_id="r-5"),
        )
        room = build_revenue(ledger, TWO_DAYS).room("r-5")
        assert room.net_cents == 0
        assert room.mean_cents_per_booking == 0.0
        assert room.forgone_cents > 0

    def test_an_empty_window_reports_nothing(self, small_ledger):
        report = build_revenue(small_ledger, DateWindow.single_day(date(2026, 11, 1)))
        assert report.rooms == ()
        assert report.net_cents == 0
        assert report.realization_rate == 0.0

    def test_a_per_room_rate_changes_only_that_room(self, small_ledger):
        table = RateTable().with_room_rate("r-2", 2400)
        report = build_revenue(small_ledger, TWO_DAYS, table)
        assert report.room("r-1").base_cents == 3600
        assert report.room("r-2").base_cents == 6000

    def test_a_flat_table_drops_every_surcharge(self, small_ledger):
        report = build_revenue(small_ledger, TWO_DAYS, RateTable.flat(1200))
        assert report.surcharge_cents == 0
        assert report.net_cents == 6600

    def test_a_week_of_traffic_values_every_room(self, week_ledger):
        report = build_revenue(week_ledger, DateWindow.from_texts("2026-10-19", "2026-10-25"))
        assert len(report.rooms) == 4
        assert report.booking_count == len(week_ledger.held())
        assert report.net_cents > 0
        assert report.forgone_cents > 0
