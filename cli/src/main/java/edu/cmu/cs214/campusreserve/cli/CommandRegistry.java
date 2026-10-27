package edu.cmu.cs214.campusreserve.cli;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The commands the tool knows, in the order they were registered.
 *
 * <p>That order is what {@code help} prints and what the unknown-command message
 * searches, so it stays fixed rather than depending on a hash order.
 */
public final class CommandRegistry {

    /**
     * How far off a typed name may be and still be offered as the intended one.
     * Two edits catches a swapped pair or a dropped letter; more than that starts
     * suggesting commands nobody was reaching for.
     */
    private static final int MAX_SUGGESTION_DISTANCE = 2;

    private final List<Command> commands;
    private final Map<String, Command> byName;

    /**
     * Builds a registry over these commands.
     *
     * @param commands at least one command, with no two sharing a name
     */
    public CommandRegistry(List<Command> commands) {
        this.commands = List.copyOf(commands);
        if (this.commands.isEmpty()) {
            throw new IllegalArgumentException("A registry needs at least one command");
        }
        Map<String, Command> registered = new LinkedHashMap<>();
        for (Command command : this.commands) {
            if (registered.put(command.name(), command) != null) {
                throw new IllegalArgumentException("Two commands share the name " + command.name());
            }
        }
        this.byName = Map.copyOf(registered);
    }

    /** The command with that name, if there is one. */
    public Optional<Command> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name));
    }

    /** Every command, in registration order. */
    public List<Command> commands() {
        return commands;
    }

    /** How many commands are registered. */
    public int size() {
        return commands.size();
    }

    /**
     * The registered name closest to {@code typed}, when one is close enough to
     * be worth offering. A name that shares no more than two edits with what was
     * typed counts; anything further away comes back empty.
     */
    public Optional<String> closestName(String typed) {
        if (typed == null || typed.isBlank()) {
            return Optional.empty();
        }
        return commands.stream()
            .map(Command::name)
            .min(Comparator.comparingInt(candidate -> editDistance(typed, candidate)))
            .filter(candidate -> editDistance(typed, candidate) <= MAX_SUGGESTION_DISTANCE);
    }

    /** What to print when a name matches nothing, with a suggestion when there is one. */
    public String unknownCommandMessage(String typed) {
        String head = "Unknown command: " + typed;
        return closestName(typed).map(name -> head + ". Did you mean " + name + "?").orElse(head);
    }

    /**
     * How many single-character insertions, deletions, and replacements turn one
     * word into the other. Two rows of the usual table are enough, because a row
     * only ever reads the one above it.
     */
    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
