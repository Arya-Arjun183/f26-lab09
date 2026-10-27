package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the event log in memory and writes it out on demand.
 *
 * <p>This is what a process uses when it wants the whole log available for an
 * export rather than a file open for the life of the process. The CLI's export
 * command drains one of these into events.jsonl.
 */
public final class InMemoryEventLog implements EventLogWriter {

    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public synchronized void append(DomainEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    @Override
    public void flush() {
        // Nothing is buffered: the events are already in the list.
    }

    @Override
    public void close() {
        // Nothing is held open.
    }

    @Override
    public synchronized long appended() {
        return events.size();
    }

    /** Everything appended so far, in order. */
    public synchronized List<DomainEvent> events() {
        return List.copyOf(events);
    }

    /** Everything appended so far, one JSON line each, in order. */
    public synchronized List<String> lines() {
        return events.stream().map(EventLogFormat::toLine).toList();
    }

    /** Events of one wire name, in order. */
    public synchronized List<DomainEvent> ofType(String type) {
        return events.stream().filter(event -> event.type().equals(type)).toList();
    }

    /**
     * Writes the whole log to {@code path} as JSON Lines, replacing whatever was
     * there. Returns how many lines were written.
     */
    public synchronized long writeTo(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = lines();
            Files.write(path, lines, StandardCharsets.UTF_8);
            return lines.size();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write the event log to " + path, failure);
        }
    }

    /** Drops everything. */
    public synchronized void clear() {
        events.clear();
    }
}
