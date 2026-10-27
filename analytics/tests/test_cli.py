"""The command line entry point."""

from __future__ import annotations

import pytest

from campusreserve_analytics.cli import EXIT_BAD_INPUT, main, parse_room_rates
from campusreserve_analytics.revenue import RateError


class TestUtilizationCommand:
    def test_prints_a_report_for_the_whole_log(self, small_log, capsys):
        assert main(["utilization", "--events", str(small_log)]) == 0
        printed = capsys.readouterr().out
        assert "Utilization for 2026-10-20 to 2026-10-21" in printed
        assert "Total 4 bookings over 330 minutes" in printed

    def test_honours_an_explicit_window(self, small_log, capsys):
        assert main([
            "utilization", "--events", str(small_log), "--from", "2026-10-21", "--to", "2026-10-21"
        ]) == 0
        printed = capsys.readouterr().out
        assert "Utilization for 2026-10-21" in printed
        assert "Total 1 bookings" in printed

    def test_prints_csv_when_asked(self, small_log, capsys):
        assert main(["utilization", "--events", str(small_log), "--format", "csv"]) == 0
        printed = capsys.readouterr().out
        assert printed.splitlines()[0].startswith("room,bookings,minutes")

    def test_a_different_open_day_changes_occupancy(self, small_log, capsys):
        main(["utilization", "--events", str(small_log), "--open-minutes", "600", "--format", "csv"])
        printed = capsys.readouterr().out
        assert printed.splitlines()[1].endswith("0.1500")


class TestRevenueCommand:
    def test_prints_a_report(self, small_log, capsys):
        assert main(["revenue", "--events", str(small_log)]) == 0
        assert "Net 85.20 over 4 bookings" in capsys.readouterr().out

    def test_takes_a_default_rate(self, small_log, capsys):
        assert main(["revenue", "--events", str(small_log), "--hourly-cents", "600"]) == 0
        assert "Default rate 6.00 an hour" in capsys.readouterr().out

    def test_takes_a_per_room_rate(self, small_log, capsys):
        assert main([
            "revenue", "--events", str(small_log), "--room-rate", "r-2=2400", "--format", "csv"
        ]) == 0
        rows = capsys.readouterr().out.splitlines()
        assert rows[1].startswith("r-2,")

    def test_takes_a_peak_percentage(self, small_log, capsys):
        assert main(["revenue", "--events", str(small_log), "--peak-percent", "0"]) == 0
        printed = capsys.readouterr().out
        assert "at 0%" in printed
        assert "Net 68.70 over 4 bookings (2.70 of it surcharges)" in printed


class TestFailures:
    def test_a_missing_log_is_reported_on_stderr(self, tmp_path, capsys):
        assert main(["utilization", "--events", str(tmp_path / "gone.jsonl")]) == EXIT_BAD_INPUT
        assert "no event log at" in capsys.readouterr().err

    def test_a_malformed_log_names_the_line(self, fixture_dir, capsys):
        assert main(["utilization", "--events", str(fixture_dir / "events-malformed.jsonl")]) == \
            EXIT_BAD_INPUT
        assert "line 3" in capsys.readouterr().err

    def test_half_a_window_is_refused(self, small_log, capsys):
        assert main(["utilization", "--events", str(small_log), "--from", "2026-10-20"]) == EXIT_BAD_INPUT
        assert "give both --from and --to" in capsys.readouterr().err

    def test_a_backwards_window_is_refused(self, small_log, capsys):
        assert main([
            "utilization", "--events", str(small_log), "--from", "2026-10-25", "--to", "2026-10-19"
        ]) == EXIT_BAD_INPUT
        assert "ends before it starts" in capsys.readouterr().err

    def test_an_empty_log_has_no_window_to_report_on(self, fixture_dir, capsys):
        assert main(["utilization", "--events", str(fixture_dir / "events-empty.jsonl")]) == EXIT_BAD_INPUT
        assert "no bookings" in capsys.readouterr().err

    def test_a_malformed_room_rate_is_refused(self, small_log, capsys):
        assert main(["revenue", "--events", str(small_log), "--room-rate", "r-2"]) == EXIT_BAD_INPUT
        assert "ROOM=CENTS" in capsys.readouterr().err

    def test_an_unknown_command_stops_the_parser(self, small_log):
        with pytest.raises(SystemExit):
            main(["nonsense", "--events", str(small_log)])


class TestRoomRates:
    def test_reads_pairs(self):
        assert parse_room_rates(["r-1=100", "r-2=250"]) == {"r-1": 100, "r-2": 250}

    def test_refuses_a_pair_with_no_equals(self):
        with pytest.raises(RateError, match="ROOM=CENTS"):
            parse_room_rates(["r-1"])

    def test_refuses_a_pair_with_no_room(self):
        with pytest.raises(RateError, match="missing a room id"):
            parse_room_rates(["=100"])

    def test_refuses_a_rate_that_is_not_a_number(self):
        with pytest.raises(RateError, match="not a whole number of cents"):
            parse_room_rates(["r-1=free"])

    def test_an_empty_list_gives_an_empty_map(self):
        assert parse_room_rates([]) == {}
