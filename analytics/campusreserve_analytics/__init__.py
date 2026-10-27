"""Reports built from the CampusReserve event log.

The Java platform appends one JSON object per line to events.jsonl. This package
reads that file and nothing else: it never opens the platform's stores and it
never writes back. Everything it knows about a booking comes from the events.
"""

__all__ = ["events", "windows", "utilization", "revenue", "render", "cli"]

__version__ = "1.0.0"
