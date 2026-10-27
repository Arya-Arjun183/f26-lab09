package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Appends events to a JSON Lines file: one JSON object per line, UTF-8, newline
 * terminated.
 *
 * <p>The file is opened for append and created if it is missing, so restarting
 * the platform continues the same log rather than truncating it. The writer is
 * synchronized because the HTTP server hands it events from several request
 * threads.
 */
public final class JsonlFileEventLogWriter implements EventLogWriter {

    private final Path path;
    private final BufferedWriter writer;
    private long appended;
    private boolean closed;

    public JsonlFileEventLogWriter(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not open the event log at " + path, failure);
        }
    }

    /** Where this writer is appending. */
    public Path path() {
        return path;
    }

    @Override
    public synchronized void append(DomainEvent event) {
        if (closed) {
            throw new IllegalStateException("The event log writer is closed: " + path);
        }
        try {
            writer.write(EventLogFormat.toLine(event));
            writer.newLine();
            appended++;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not append to the event log at " + path, failure);
        }
    }

    @Override
    public synchronized void flush() {
        if (closed) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not flush the event log at " + path, failure);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.close();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not close the event log at " + path, failure);
        }
    }

    @Override
    public synchronized long appended() {
        return appended;
    }
}
