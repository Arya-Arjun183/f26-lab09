package edu.cmu.cs214.campusreserve.cli.commands;

import edu.cmu.cs214.campusreserve.cli.CliContext;
import edu.cmu.cs214.campusreserve.cli.Command;
import edu.cmu.cs214.campusreserve.cli.Console;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import java.util.Objects;
import java.util.Optional;

/**
 * What every command has in common: the three strings that describe it, and the
 * room lookup that three of them start with.
 *
 * <p>Holding the name, the summary, and the usage as fields means a command
 * class is its {@code run} method and nothing else.
 */
public abstract class BaseCommand implements Command {

    private final String name;
    private final String summary;
    private final String usage;

    /**
     * Names the command.
     *
     * @param name the word that selects it
     * @param summary one line for the command list
     * @param usage the shape of the command line
     */
    protected BaseCommand(String name, String summary, String usage) {
        this.name = Objects.requireNonNull(name, "name");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.usage = Objects.requireNonNull(usage, "usage");
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String summary() {
        return summary;
    }

    @Override
    public final String usage() {
        return usage;
    }

    /**
     * The room with that id. When there is none, the failure is already printed
     * and the caller returns {@link Command}'s failure code.
     */
    protected static Optional<Room> findRoom(CliContext context, Console console, String roomId) {
        Optional<Room> room = context.rooms().find(roomId);
        if (room.isEmpty()) {
            console.error("No room with that id: " + roomId);
        }
        return room;
    }
}
