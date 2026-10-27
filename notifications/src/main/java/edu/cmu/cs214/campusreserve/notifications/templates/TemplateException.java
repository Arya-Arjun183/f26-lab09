package edu.cmu.cs214.campusreserve.notifications.templates;

/**
 * A template could not be rendered.
 *
 * <p>Every rendering problem is a programming mistake: a template that asks for
 * a value nobody supplied, a caller that supplies a value no template asks for,
 * or text with a broken placeholder. Rendering fails rather than shipping a
 * message with a hole in it.
 */
public class TemplateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Builds the exception with an explanation of what did not line up. */
    public TemplateException(String message) {
        super(message);
    }
}
