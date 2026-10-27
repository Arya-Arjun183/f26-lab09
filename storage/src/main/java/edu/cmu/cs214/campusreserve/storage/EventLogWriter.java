package edu.cmu.cs214.campusreserve.storage;

import edu.cmu.cs214.campusreserve.core.events.DomainEvent;

/**
 * Where the event log goes. Writers append one line per event and never rewrite
 * a line they have already written.
 *
 * <p>Storage is the only module that writes the event log. Anything else that
 * wants events subscribes to the publisher instead.
 */
public interface EventLogWriter extends AutoCloseable {

    /** Appends one event to the log. */
    void append(DomainEvent event);

    /** Pushes anything buffered out to wherever the log lives. */
    void flush();

    /** Flushes and releases whatever the writer holds open. */
    @Override
    void close();

    /** How many events this writer has appended. */
    long appended();
}
