"""Turning a report into text or CSV."""

from __future__ import annotations

import csv
import io

import pytest

from campusreserve_analytics.render import (
    format_table,
    render_revenue,
    render_utilization,
    revenue_csv,
    utilization_csv,
)
from campusreserve_analytics.revenue import RateTable, build_revenue
from campusreserve_analytics.utilization import build_utilization
from campusreserve_analytics.windows import DateWindow

TWO_DAYS = DateWindow.from_texts("2026-10-20", "2026-10-21")


class TestFormatTable:
    def test_pads_every_column_to_its_widest_cell(self):
        rendered = format_table(["room", "count"], [["r-1", "3"], ["room-longer", "10"]])
        lines = rendered.splitlines()
        assert lines[0].startswith("room       ")
        assert len(lines) == 4

    def test_puts_a_rule_under_the_header(self):
        rendered = format_table(["a", "b"], [["1", "2"]])
        assert rendered.splitlines()[1] == "-  -"

    def test_right_aligns_numeric_columns(self):
        rendered = format_table(["room", "minutes"], [["r-1", "5"], ["r-2", "120"]])
        rows = rendered.splitlines()[2:]
        assert rows[0].endswith("  5")
        assert rows[1].endswith("120")

    def test_leaves_the_first_column_left_aligned(self):
        rendered = format_table(["id", "n"], [["1", "1"], ["222", "2"]])
        assert rendered.splitlines()[2].startswith("1  ")

    def test_refuses_a_row_of_the_wrong_width(self):
        with pytest.raises(ValueError, match="row has 1 cells"):
            format_table(["a", "b"], [["1"]])

    def test_refuses_a_table_with_no_columns(self):
        with pytest.raises(ValueError, match="at least one column"):
            format_table([], [])

    def test_renders_a_header_only_table(self):
        assert format_table(["a"], []).splitlines() == ["a", "-"]


class TestUtilizationText:
    def test_names_the_window_and_the_rooms(self, small_ledger):
        rendered = render_utilization(build_utilization(small_ledger, TWO_DAYS))
        assert "Utilization for 2026-10-20 to 2026-10-21" in rendered
        assert "r-1" in rendered
        assert "r-2" in rendered

    def test_reports_the_totals(self, small_ledger):
        rendered = render_utilization(build_utilization(small_ledger, TWO_DAYS))
        assert "Total 4 bookings over 330 minutes" in rendered
        assert "1 from the waitlist" in rendered
        assert "1 cancelled" in rendered

    def test_reports_the_busiest_hour(self, small_ledger):
        rendered = render_utilization(build_utilization(small_ledger, TWO_DAYS))
        assert "Busiest start hour 09:00 UTC" in rendered

    def test_stays_quiet_about_orphan_cancellations_when_there_are_none(self, small_ledger):
        rendered = render_utilization(build_utilization(small_ledger, TWO_DAYS))
        assert "never opened" not in rendered

    def test_an_empty_report_still_renders(self, small_ledger):
        report = build_utilization(small_ledger, DateWindow.from_texts("2026-11-01", "2026-11-02"))
        rendered = render_utilization(report)
        assert "Total 0 bookings over 0 minutes" in rendered
        assert "Busiest start hour" not in rendered


class TestRevenueText:
    def test_names_the_window_and_the_rate(self, small_ledger):
        rendered = render_revenue(build_revenue(small_ledger, TWO_DAYS))
        assert "Revenue for 2026-10-20 to 2026-10-21" in rendered
        assert "Default rate 12.00 an hour" in rendered

    def test_reports_the_totals(self, small_ledger):
        rendered = render_revenue(build_revenue(small_ledger, TWO_DAYS))
        assert "Net 85.20 over 4 bookings" in rendered
        assert "Forgone to cancellations 30.00" in rendered

    def test_shows_the_peak_window_the_table_used(self, small_ledger):
        rendered = render_revenue(build_revenue(small_ledger, TWO_DAYS, RateTable(peak_first_hour=8,
                                                                                 peak_last_hour=12)))
        assert "peak 08:00 to 12:59 UTC" in rendered


class TestCsv:
    def test_utilization_csv_has_a_header_and_a_row_per_room(self, small_ledger):
        rendered = utilization_csv(build_utilization(small_ledger, TWO_DAYS))
        rows = list(csv.reader(io.StringIO(rendered)))
        assert rows[0] == ["room", "bookings", "minutes", "mean_minutes", "promoted", "cancelled",
                           "occupancy"]
        assert rows[1][0] == "r-1"
        assert rows[1][2] == "180"
        assert len(rows) == 3

    def test_revenue_csv_reports_whole_cents(self, small_ledger):
        rendered = revenue_csv(build_revenue(small_ledger, TWO_DAYS))
        rows = list(csv.reader(io.StringIO(rendered)))
        assert rows[0][-1] == "forgone_cents"
        assert rows[1][6] == "4500"

    def test_csv_output_has_no_trailing_blank_line(self, small_ledger):
        rendered = revenue_csv(build_revenue(small_ledger, TWO_DAYS))
        assert not rendered.endswith("\n")
