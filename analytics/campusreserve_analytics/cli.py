"""Command line entry point for the reports.

    python -m campusreserve_analytics utilization --events events.jsonl
    python -m campusreserve_analytics revenue --events events.jsonl --format csv

With no --from and --to, the window is whatever the log covers.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from .events import EventLogError, Ledger, load_ledger
from .render import render_revenue, render_utilization, revenue_csv, utilization_csv
from .revenue import RateError, RateTable, build_revenue
from .utilization import DEFAULT_OPEN_MINUTES_PER_DAY, build_utilization
from .windows import DateWindow, WindowError

DEFAULT_EVENT_LOG = "events.jsonl"

#: Returned when the log or the arguments could not be used.
EXIT_BAD_INPUT = 2


def build_parser() -> argparse.ArgumentParser:
    """The argument parser, exposed so the tests can check the flags."""
    parser = argparse.ArgumentParser(
        prog="campusreserve-analytics",
        description="Reports built from the CampusReserve event log.",
    )
    subcommands = parser.add_subparsers(dest="command", required=True)

    utilization = subcommands.add_parser("utilization", help="how much of the bookable day sold")
    _add_common_arguments(utilization)
    utilization.add_argument(
        "--open-minutes",
        type=int,
        default=DEFAULT_OPEN_MINUTES_PER_DAY,
        help="bookable minutes in a day (default %(default)s)",
    )

    revenue = subcommands.add_parser("revenue", help="what the sold time was worth")
    _add_common_arguments(revenue)
    revenue.add_argument(
        "--hourly-cents",
        type=int,
        default=None,
        help="rate for rooms the table does not name",
    )
    revenue.add_argument(
        "--room-rate",
        action="append",
        default=[],
        metavar="ROOM=CENTS",
        help="hourly rate for one room, repeatable",
    )
    revenue.add_argument(
        "--peak-percent",
        type=int,
        default=None,
        help="surcharge on bookings that start in the peak window",
    )

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """Runs one command. Returns the process exit code."""
    parser = build_parser()
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        ledger = load_ledger(Path(args.events))
        window = _window_for(args, ledger)
        if args.command == "utilization":
            output = _utilization_output(args, ledger, window)
        else:
            output = _revenue_output(args, ledger, window)
    except (EventLogError, WindowError, RateError, ValueError) as failure:
        print(f"campusreserve-analytics: {failure}", file=sys.stderr)
        return EXIT_BAD_INPUT

    print(output)
    return 0


def parse_room_rates(pairs: Sequence[str]) -> dict[str, int]:
    """Turns ROOM=CENTS strings into a rate map, refusing anything malformed."""
    rates: dict[str, int] = {}
    for pair in pairs:
        if "=" not in pair:
            raise RateError(f"room rate must look like ROOM=CENTS, got {pair!r}")
        room_id, _, amount = pair.partition("=")
        room_id = room_id.strip()
        if not room_id:
            raise RateError(f"room rate is missing a room id: {pair!r}")
        try:
            rates[room_id] = int(amount)
        except ValueError as failure:
            raise RateError(f"room rate for {room_id} is not a whole number of cents: {amount!r}") from failure
    return rates


def _add_common_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--events",
        default=DEFAULT_EVENT_LOG,
        help="path to the event log (default %(default)s)",
    )
    parser.add_argument("--from", dest="first_day", default=None, metavar="YYYY-MM-DD")
    parser.add_argument("--to", dest="last_day", default=None, metavar="YYYY-MM-DD")
    parser.add_argument(
        "--format",
        dest="output_format",
        choices=("text", "csv"),
        default="text",
        help="how to print the report (default %(default)s)",
    )


def _window_for(args: argparse.Namespace, ledger: Ledger) -> DateWindow:
    if args.first_day and args.last_day:
        return DateWindow.from_texts(args.first_day, args.last_day)
    if args.first_day or args.last_day:
        raise WindowError("give both --from and --to, or neither")
    starts = [record.start for record in ledger.bookings.values()]
    if not starts:
        raise WindowError("the event log holds no bookings, so there is no window to report on")
    return DateWindow.spanning(starts)


def _utilization_output(args: argparse.Namespace, ledger: Ledger, window: DateWindow) -> str:
    report = build_utilization(ledger, window, open_minutes_per_day=args.open_minutes)
    return utilization_csv(report) if args.output_format == "csv" else render_utilization(report)


def _revenue_output(args: argparse.Namespace, ledger: Ledger, window: DateWindow) -> str:
    rates = RateTable(
        default_hourly_cents=args.hourly_cents if args.hourly_cents is not None else RateTable().default_hourly_cents,
        per_room_hourly_cents=parse_room_rates(args.room_rate),
        peak_surcharge_percent=(
            args.peak_percent if args.peak_percent is not None else RateTable().peak_surcharge_percent
        ),
    )
    report = build_revenue(ledger, window, rates)
    return revenue_csv(report) if args.output_format == "csv" else render_revenue(report)
