package edu.cmu.cs214.campusreserve.core.domain;

/**
 * What a room is used for. The kind carries the two limits that vary by room
 * type: how long a single booking may run, and how far ahead it may be made.
 */
public enum RoomKind {

    /** Small rooms for two to eight people, bookable at short notice. */
    STUDY_ROOM("Study room", 180, 14),

    /** Teaching rooms, bookable a term ahead. */
    CLASSROOM("Classroom", 240, 90),

    /** Equipment rooms with a shorter cap so slots rotate. */
    LAB("Lab", 120, 30),

    /** Large venues that need planning lead time. */
    AUDITORIUM("Auditorium", 480, 180);

    private final String displayName;
    private final int maxDurationMinutes;
    private final int maxAdvanceDays;

    RoomKind(String displayName, int maxDurationMinutes, int maxAdvanceDays) {
        this.displayName = displayName;
        this.maxDurationMinutes = maxDurationMinutes;
        this.maxAdvanceDays = maxAdvanceDays;
    }

    /** Human-readable label used by the CLI and the HTTP responses. */
    public String displayName() {
        return displayName;
    }

    /** Longest single booking allowed in a room of this kind. */
    public int maxDurationMinutes() {
        return maxDurationMinutes;
    }

    /** Furthest ahead a booking may start in a room of this kind. */
    public int maxAdvanceDays() {
        return maxAdvanceDays;
    }

    /** Looks up a kind by name, case insensitive. */
    public static RoomKind fromText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Room kind is required");
        }
        String normalized = text.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (RoomKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown room kind: " + text);
    }
}
