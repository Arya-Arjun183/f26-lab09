"""How much of the bookable day each room sold."""

from __future__ import annotations

from datetime import date

import pytest

from campusreserve_analytics.utilization import (
    DEFAULT_OPEN_MINUTES_PER_DAY,
    build_utilization,
)
from campusreserve_analytics.windows import DateWindow

from .conftest import ledger_from_lines, make_line

TWO_DAYS = DateWindow.from_texts("2026-10-20", "2026-10-21")


class TestRoomRows:
    def test_counts_held_minutes_per_room(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.room("r-1").booked_minutes == 180
        assert report.room("r-2").booked_minutes == 150

    def test_cancelled_bookings_do_not_count_as_booked_time(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.room("r-1").booking_count == 2
        assert report.room("r-1").cancelled_count == 1

    def test_promotions_count_as_bookings_and_are_reported_separately(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.room("r-1").promoted_count == 1
        assert report.total_promotions == 1

    def test_rooms_come_back_busiest_first(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert [room.room_id for room in report.rooms] == ["r-1", "r-2"]

    def test_ties_are_broken_by_room_id(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", room_id="r-9"),
            make_line(booking_id="bk-2", room_id="r-2", start="2026-10-20T13:00:00Z",
                      end="2026-10-20T14:00:00Z"),
        )
        report = build_utilization(ledger, TWO_DAYS)
        assert [room.room_id for room in report.rooms] == ["r-2", "r-9"]

    def test_a_room_with_only_cancellations_still_gets_a_row(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", room_id="r-5"),
            make_line(event="booking-cancelled", booking_id="bk-1", room_id="r-5"),
        )
        report = build_utilization(ledger, TWO_DAYS)
        assert report.room("r-5").booking_count == 0
        assert report.room("r-5").cancelled_count == 1
        assert report.room("r-5").booked_minutes == 0

    def test_mean_duration_and_party_size(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.room("r-1").mean_duration_minutes == pytest.approx(90.0)
        assert report.room("r-1").mean_party_size == pytest.approx(3.5)

    def test_means_are_zero_when_nothing_was_held(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", room_id="r-5"),
            make_line(event="booking-cancelled", booking_id="bk-1", room_id="r-5"),
        )
        room = build_utilization(ledger, TWO_DAYS).room("r-5")
        assert room.mean_duration_minutes == 0.0
        assert room.mean_party_size == 0.0
        assert room.cancellation_rate == pytest.approx(1.0)

    def test_occupancy_is_minutes_over_open_minutes_times_days(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.room("r-1").occupancy_rate == pytest.approx(180 / (960 * 2))

    def test_occupancy_never_passes_one(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", start="2026-10-20T06:00:00Z", end="2026-10-20T22:00:00Z"),
        )
        report = build_utilization(ledger, DateWindow.single_day(date(2026, 10, 20)), 60)
        assert report.room("r-1").occupancy_rate == pytest.approx(1.0)


class TestDayRows:
    def test_one_row_per_day_in_the_window(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert [row.day for row in report.days] == [date(2026, 10, 20), date(2026, 10, 21)]

    def test_a_day_row_adds_up_every_room(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        first = report.day(date(2026, 10, 20))
        assert first.booking_count == 3
        assert first.booked_minutes == 270
        assert first.room_count == 2
        assert first.mean_minutes_per_room == pytest.approx(135.0)

    def test_a_quiet_day_reports_zeroes(self, small_ledger):
        report = build_utilization(small_ledger, DateWindow.from_texts("2026-10-20", "2026-10-22"))
        quiet = report.day(date(2026, 10, 22))
        assert quiet.booking_count == 0
        assert quiet.booked_minutes == 0
        assert quiet.mean_minutes_per_room == 0.0

    def test_a_day_outside_the_window_has_no_row(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.day(date(2026, 11, 1)) is None


class TestTotals:
    def test_adds_up_every_room(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.total_booked_minutes == 330
        assert report.total_bookings == 4
        assert report.total_cancellations == 1

    def test_capacity_is_rooms_times_open_minutes_times_days(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.capacity_minutes == 2 * 960 * 2
        assert report.overall_occupancy == pytest.approx(330 / 3840)

    def test_an_empty_window_reports_no_occupancy(self, small_ledger):
        report = build_utilization(small_ledger, DateWindow.from_texts("2026-11-01", "2026-11-02"))
        assert report.rooms == ()
        assert report.capacity_minutes == 0
        assert report.overall_occupancy == 0.0
        assert report.busiest_start_hour is None

    def test_the_start_hour_histogram_counts_held_bookings(self, small_ledger):
        report = build_utilization(small_ledger, TWO_DAYS)
        assert report.start_hour_histogram == {9: 2, 10: 1, 13: 1}
        assert report.busiest_start_hour == 9

    def test_a_tie_in_the_histogram_picks_the_earlier_hour(self):
        ledger = ledger_from_lines(
            make_line(booking_id="bk-1", start="2026-10-20T08:00:00Z", end="2026-10-20T09:00:00Z"),
            make_line(booking_id="bk-2", start="2026-10-20T15:00:00Z", end="2026-10-20T16:00:00Z"),
        )
        assert build_utilization(ledger, TWO_DAYS).busiest_start_hour == 8

    def test_reports_cancellations_that_name_an_unseen_booking(self):
        ledger = ledger_from_lines(make_line(event="booking-cancelled", booking_id="bk-ghost"))
        report = build_utilization(ledger, TWO_DAYS)
        assert report.cancellations_without_a_booking == 1


class TestWindowing:
    def test_bookings_outside_the_window_are_left_out(self, small_ledger):
        report = build_utilization(small_ledger, DateWindow.single_day(date(2026, 10, 21)))
        assert report.total_bookings == 1
        assert report.room("r-1") is None

    def test_a_week_of_traffic_adds_up(self, week_ledger):
        window = DateWindow.from_texts("2026-10-19", "2026-10-25")
        report = build_utilization(week_ledger, window)
        assert len(report.rooms) == 4
        assert report.window.day_count == 7
        assert report.total_booked_minutes == sum(
            record.duration_minutes for record in week_ledger.held()
        )
        assert report.total_bookings == len(week_ledger.held())
        assert report.total_promotions == len(week_ledger.promoted())

    def test_the_open_minutes_default_matches_the_platform_policy(self):
        assert DEFAULT_OPEN_MINUTES_PER_DAY == 960

    def test_refuses_a_non_positive_open_day(self, small_ledger):
        with pytest.raises(ValueError, match="must be positive"):
            build_utilization(small_ledger, TWO_DAYS, 0)
