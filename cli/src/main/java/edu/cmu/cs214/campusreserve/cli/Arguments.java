package edu.cmu.cs214.campusreserve.cli;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The tokens after the command name, split into flags and positional words.
 *
 * <p>A flag is written {@code --name value}. A flag with no value after it (the
 * next token is another flag, or there is no next token) is a switch, and
 * {@link #isPresent(String)} is how a command reads one. A word after a flag is
 * that flag's value, so a switch goes last on a line. Anything else is
 * positional.
 *
 * <p>A command states which flags it takes by calling {@link #allow(String...)}
 * before it reads anything. A flag outside that list is refused rather than
 * ignored, so a typed flag name that the command would never look at stops the
 * run instead of quietly doing nothing.
 *
 * <p>Every typed accessor names the flag it is unhappy about, because the person
 * reading the failure is holding a command line, not a stack trace.
 */
public final class Arguments {

    /** Value stored for a flag that was given without one. */
    private static final String SWITCH = "";

    private final Map<String, String> flags = new LinkedHashMap<>();
    private final List<String> positionals = new ArrayList<>();

    private Arguments(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!token.startsWith("--")) {
                positionals.add(token);
                continue;
            }
            String name = token.substring(2);
            if (name.isEmpty()) {
                throw new UsageException("A flag needs a name after the two dashes");
            }
            if (flags.containsKey(name)) {
                throw new UsageException("Flag --" + name + " was given twice");
            }
            String next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (next == null || next.startsWith("--")) {
                flags.put(name, SWITCH);
            } else {
                flags.put(name, next);
                i++;
            }
        }
    }

    /**
     * Splits raw tokens into flags and positional words.
     *
     * @param tokens the words after the command name, in the order typed
     * @throws UsageException on a bare {@code --}, or a flag given twice
     */
    public static Arguments parse(String... tokens) {
        return new Arguments(List.of(Objects.requireNonNull(tokens, "tokens")));
    }

    /**
     * Declares the flags this command takes and refuses any other.
     *
     * @param names every flag name the command reads, without the dashes
     * @return this, so a command can chain the checks it wants
     * @throws UsageException when a flag outside the list was given
     */
    public Arguments allow(String... names) {
        Set<String> known = Set.of(names);
        for (String given : flags.keySet()) {
            if (!known.contains(given)) {
                throw new UsageException("Unknown flag --" + given + ". " + (names.length == 0
                    ? "This command takes no flags."
                    : "This command takes: --" + String.join(", --", names)));
            }
        }
        return this;
    }

    /** Refuses stray words, so a value typed without its flag does not vanish. */
    public Arguments requireNoPositionals() {
        return requireAtMostPositionals(0);
    }

    /** Refuses more positional words than the command reads. */
    public Arguments requireAtMostPositionals(int limit) {
        if (positionals.size() > limit) {
            throw new UsageException("Unexpected argument: " + positionals.get(limit));
        }
        return this;
    }

    /** The positional word at that index, if there is one. */
    public Optional<String> positional(int index) {
        return index < positionals.size() ? Optional.of(positionals.get(index)) : Optional.empty();
    }

    /** Was the flag given at all, with or without a value? */
    public boolean isPresent(String flag) {
        return flags.containsKey(flag);
    }

    /**
     * The value of a flag the command cannot run without.
     *
     * @throws UsageException when the flag is missing or was given without a value
     */
    public String requireString(String flag) {
        String value = flags.get(flag);
        if (value == null) {
            throw new UsageException("Flag --" + flag + " is required");
        }
        if (value.equals(SWITCH)) {
            throw new UsageException("Flag --" + flag + " needs a value");
        }
        return value;
    }

    /** The value of an optional flag, or empty when it was not given. */
    public Optional<String> optionalString(String flag) {
        return isPresent(flag) ? Optional.of(requireString(flag)) : Optional.empty();
    }

    /** The value of an optional flag, or {@code fallback} when it was not given. */
    public String stringOrDefault(String flag, String fallback) {
        return optionalString(flag).orElse(fallback);
    }

    /**
     * The whole number a required flag carries, which has to be above zero: every
     * number this tool takes is a count of minutes, seats, or people.
     *
     * @throws UsageException when the flag is missing, is not a whole number, or is not positive
     */
    public int requireCount(String flag) {
        String value = requireString(flag);
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new UsageException("Flag --" + flag + " needs a whole number, and got " + value);
        }
        if (number <= 0) {
            throw new UsageException("Flag --" + flag + " must be above zero, and got " + number);
        }
        return number;
    }

    /** The count an optional flag carries, or {@code fallback} when it was not given. */
    public int countOrDefault(String flag, int fallback) {
        return isPresent(flag) ? requireCount(flag) : fallback;
    }

    /**
     * The instant a required flag carries, written ISO-8601 in UTC, for example
     * {@code 2026-10-19T14:00:00Z}.
     *
     * @throws UsageException when the flag is missing or is not such an instant
     */
    public Instant requireInstant(String flag) {
        return timeValue(flag, Instant::parse, "an ISO-8601 instant such as 2026-10-19T14:00:00Z");
    }

    /**
     * The UTC calendar date a required flag carries, written {@code YYYY-MM-DD}.
     *
     * @throws UsageException when the flag is missing or is not such a date
     */
    public LocalDate requireDate(String flag) {
        return timeValue(flag, LocalDate::parse, "a date written YYYY-MM-DD");
    }

    /** Runs one of the {@code java.time} parsers, reporting the flag and the shape it wanted. */
    private <T> T timeValue(String flag, Function<String, T> parser, String shape) {
        String value = requireString(flag);
        try {
            return parser.apply(value);
        } catch (DateTimeParseException failure) {
            throw new UsageException("Flag --" + flag + " needs " + shape + ", and got " + value);
        }
    }
}
