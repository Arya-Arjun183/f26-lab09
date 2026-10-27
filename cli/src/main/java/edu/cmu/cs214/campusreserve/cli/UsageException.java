package edu.cmu.cs214.campusreserve.cli;

/**
 * The caller typed something the command cannot act on: a missing flag, a value
 * in the wrong shape, a flag the command does not take.
 *
 * <p>This is separate from a command that ran and failed. The entry point turns
 * a usage complaint into exit code 2 and prints the command's usage line next to
 * it; a command that ran and failed exits 1 and prints only its own message.
 */
public final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Builds a usage complaint with a message written for the person typing. */
    public UsageException(String message) {
        super(message);
    }
}
