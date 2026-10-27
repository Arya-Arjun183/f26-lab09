"""The date range a report covers.

Reports work in whole UTC days. A window is inclusive at both ends, because that
is what an operator means when they say "the week of the 19th to the 25th".
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Iterable, Iterator, Sequence


class WindowError(ValueError):
    """A date range could not be built."""


def parse_date(text: str, *, field_name: str = "date") -> date:
    """Parses a plain YYYY-MM-DD date."""
    if not isinstance(text, str):
        raise WindowError(f"{field_name} must be a string, got {type(text).__name__}")
    try:
        return date.fromisoformat(text.strip())
    except ValueError as failure:
        raise WindowError(f"{field_name} is not a YYYY-MM-DD date: {text!r}") from failure


@dataclass(frozen=True)
class DateWindow:
    """A run of whole UTC days, first and last day included."""

    first: date
    last: date

    def __post_init__(self) -> None:
        if self.last < self.first:
            raise WindowError(f"window ends before it starts: {self.first} to {self.last}")

    @classmethod
    def of(cls, first: date, last: date) -> "DateWindow":
        """Builds a window from two dates."""
        return cls(first, last)

    @classmethod
    def from_texts(cls, first_text: str, last_text: str) -> "DateWindow":
        """Builds a window from two YYYY-MM-DD strings."""
        return cls(parse_date(first_text, field_name="from"), parse_date(last_text, field_name="to"))

    @classmethod
    def single_day(cls, day: date) -> "DateWindow":
        """A window covering one day."""
        return cls(day, day)

    @classmethod
    def spanning(cls, moments: Sequence[datetime | date]) -> "DateWindow":
        """The smallest window covering every moment given.

        Used when the caller did not name a range and wants whatever the log
        happens to hold.
        """
        if not moments:
            raise WindowError("cannot build a window from nothing")
        days = [moment.date() if isinstance(moment, datetime) else moment for moment in moments]
        return cls(min(days), max(days))

    @property
    def day_count(self) -> int:
        """How many days the window covers, both ends included."""
        return (self.last - self.first).days + 1

    def days(self) -> Iterator[date]:
        """Every day in the window, in order."""
        current = self.first
        while current <= self.last:
            yield current
            current += timedelta(days=1)

    def contains(self, moment: datetime | date) -> bool:
        """Does the window cover the UTC day this moment falls on?"""
        day = moment.date() if isinstance(moment, datetime) else moment
        return self.first <= day <= self.last

    def filter(self, records: Iterable) -> list:
        """Keeps the records whose start date falls in the window."""
        return [record for record in records if self.contains(record.start)]

    def label(self) -> str:
        """A short description, for example 2026-10-19 to 2026-10-25."""
        if self.first == self.last:
            return self.first.isoformat()
        return f"{self.first.isoformat()} to {self.last.isoformat()}"

    def __str__(self) -> str:
        return self.label()
