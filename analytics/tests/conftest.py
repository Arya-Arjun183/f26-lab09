"""Shared fixtures for the analytics tests.

The JSONL files under tests/fixtures are exactly what the Java platform writes,
so a change to the event log format shows up here first.
"""

from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from campusreserve_analytics.events import build_ledger, load_events, load_ledger

FIXTURE_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture
def fixture_dir() -> Path:
    """Where the sample event logs live."""
    return FIXTURE_DIR


@pytest.fixture
def small_log() -> Path:
    """A six-line log with a creation, a cancellation, and a promotion."""
    return FIXTURE_DIR / "events-small.jsonl"


@pytest.fixture
def week_log() -> Path:
    """A week of traffic across four rooms."""
    return FIXTURE_DIR / "events-week.jsonl"


@pytest.fixture
def small_ledger():
    """The small log, folded into per-booking records."""
    return load_ledger(FIXTURE_DIR / "events-small.jsonl")


@pytest.fixture
def week_ledger():
    """The week log, folded into per-booking records."""
    return load_ledger(FIXTURE_DIR / "events-week.jsonl")


@pytest.fixture
def small_events():
    """The small log, as parsed events in file order."""
    return load_events(FIXTURE_DIR / "events-small.jsonl")


def make_line(
    event: str = "booking-created",
    booking_id: str = "bk-1",
    room_id: str = "r-1",
    user_id: str = "u-1",
    start: str = "2026-10-20T10:00:00Z",
    end: str = "2026-10-20T11:00:00Z",
    attendees: int = 4,
    occurred_at: str = "2026-10-19T09:00:00Z",
    **extra: object,
) -> str:
    """Builds one event log line, so a test can vary exactly one field."""
    body = {
        "event": event,
        "occurredAt": occurred_at,
        "bookingId": booking_id,
        "roomId": room_id,
        "userId": user_id,
        "start": start,
        "end": end,
        "attendees": attendees,
    }
    body.update(extra)
    return json.dumps(body, separators=(",", ":"))


def ledger_from_lines(*lines: str):
    """Folds a handful of hand-written lines into a ledger."""
    from campusreserve_analytics.events import iter_events

    return build_ledger(list(iter_events(lines)))


def instant(text: str) -> datetime:
    """Parses an ISO-8601 UTC instant for use in assertions."""
    return datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(timezone.utc)


def minutes_later(text: str, minutes: int) -> str:
    """A UTC instant a number of minutes after the one given."""
    moved = instant(text) + timedelta(minutes=minutes)
    return moved.strftime("%Y-%m-%dT%H:%M:%SZ")
