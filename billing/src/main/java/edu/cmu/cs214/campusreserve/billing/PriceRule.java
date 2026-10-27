package edu.cmu.cs214.campusreserve.billing;

import java.util.List;

/**
 * One step of the price pipeline. A rule reads a {@link PriceContext} and
 * contributes zero, one, or several {@link LineItem}s.
 *
 * <p>A rule that has nothing to say returns an empty list rather than a line
 * worth nothing, so a quote never carries a row of zeroes.
 *
 * <p>Rules hold no state. Everything they need to decide comes from the context
 * and its rate card, which is what makes a quote reproducible from the booking
 * alone.
 */
public interface PriceRule {

    /** The code the rule stamps on the lines it produces, for example {@code PEAK}. */
    String code();

    /**
     * The lines this rule adds to the quote.
     *
     * @param context the room, the time, the party size, and the prices
     * @param runningTotal the sum of the lines produced by the rules that ran
     *     before this one, in the context's currency. Most rules ignore it. A
     *     rule that reads it depends on where it sits in the pipeline, so
     *     {@link PriceCalculator} fixes the order and documents it.
     * @return the lines to append, possibly empty, never null
     */
    List<LineItem> apply(PriceContext context, Money runningTotal);
}
