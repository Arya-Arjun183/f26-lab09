package edu.cmu.cs214.campusreserve.storage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A map of records keyed by id, with the read helpers every store in the
 * platform needs. Concrete stores extend this and add domain-shaped queries.
 *
 * <p>Records are immutable, so a store hands out the same instance it holds and
 * a caller cannot change what is stored behind its back. Writes replace whole
 * records rather than mutating them.
 *
 * @param <T> the record type
 */
public abstract class InMemoryRecordStore<T> {

    private final Map<String, T> records = new ConcurrentHashMap<>();
    private final Function<T, String> idOf;

    protected InMemoryRecordStore(Function<T, String> idOf) {
        this.idOf = Objects.requireNonNull(idOf, "idOf");
    }

    /** Inserts the record, or replaces the one already under its id. */
    protected void put(T record) {
        Objects.requireNonNull(record, "record");
        records.put(idOf.apply(record), record);
    }

    /** The record with that id, if there is one. */
    protected Optional<T> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(records.get(id));
    }

    /** Drops the record. Returns whether there was one to drop. */
    protected boolean delete(String id) {
        return id != null && records.remove(id) != null;
    }

    /** Every record, in no particular order. */
    protected List<T> values() {
        return List.copyOf(records.values());
    }

    /** Every record that satisfies {@code filter}, sorted by {@code order}. */
    protected List<T> select(Predicate<T> filter, Comparator<T> order) {
        return records.values().stream().filter(filter).sorted(order).toList();
    }

    /** Every record sorted by {@code order}. */
    protected List<T> sorted(Comparator<T> order) {
        return records.values().stream().sorted(order).toList();
    }

    /** Does the store hold a record with that id? */
    public boolean contains(String id) {
        return id != null && records.containsKey(id);
    }

    /** How many records the store holds. */
    public int count() {
        return records.size();
    }

    /** Is the store empty? */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** Drops everything. Used when a process wants a clean slate. */
    public void clear() {
        records.clear();
    }
}
