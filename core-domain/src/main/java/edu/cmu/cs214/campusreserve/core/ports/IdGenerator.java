package edu.cmu.cs214.campusreserve.core.ports;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Hands out the identifiers the service puts on new records. */
@FunctionalInterface
public interface IdGenerator {

    /** A fresh id starting with {@code prefix}, for example {@code bk-7}. */
    String nextId(String prefix);

    /** Ids that count up from one. Deterministic, so tests can name records. */
    static IdGenerator sequential() {
        AtomicLong counter = new AtomicLong();
        return prefix -> prefix + "-" + counter.incrementAndGet();
    }

    /** Ids backed by random UUIDs, for anything that outlives one process. */
    static IdGenerator random() {
        return prefix -> prefix + "-" + UUID.randomUUID();
    }
}
