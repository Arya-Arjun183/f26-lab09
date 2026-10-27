package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.billing.rules.BaseChargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.LargeGroupSurchargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.LongBookingDiscountRule;
import edu.cmu.cs214.campusreserve.billing.rules.MinimumChargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.PeakHourSurchargeRule;
import edu.cmu.cs214.campusreserve.core.domain.Room;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Runs the price rules over a booking, in order, and returns the quote.
 *
 * <p>The order is part of the price, not an implementation detail. The
 * calculator keeps a running total as it goes and hands it to each rule, and the
 * minimum charge rule is written to read it. The standard pipeline is:
 *
 * <ol>
 *   <li>{@code BASE}: the booked time at the room kind's hourly rate.
 *   <li>{@code PEAK}: a percentage of the base charge for a peak start.
 *   <li>{@code GROUP}: a percentage of the base charge for a large party.
 *   <li>{@code LONG}: a credit against the base charge for a long booking.
 *   <li>{@code MIN}: a top-up when everything above lands under the floor.
 * </ol>
 *
 * <p>Steps two through four each scale off the base charge, so they can be
 * reordered among themselves without changing a total. The floor cannot: it has
 * to see the discount already applied, otherwise a discounted booking can settle
 * under the minimum. Putting {@code MIN} anywhere but last changes what users
 * pay.
 */
public final class PriceCalculator {

    private final List<PriceRule> rules;

    /**
     * Builds a calculator that runs the given rules in the order given.
     *
     * @param rules at least one rule, with no two sharing a code
     */
    public PriceCalculator(List<PriceRule> rules) {
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("A calculator needs at least one rule");
        }
        Set<String> seen = new HashSet<>();
        for (PriceRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (!seen.add(rule.code())) {
                throw new IllegalArgumentException("Two rules share the code " + rule.code());
            }
        }
        this.rules = List.copyOf(rules);
    }

    /** The pipeline the platform bills with, in the documented order. */
    public static PriceCalculator standard() {
        return new PriceCalculator(List.of(
            new BaseChargeRule(),
            new PeakHourSurchargeRule(),
            new LargeGroupSurchargeRule(),
            new LongBookingDiscountRule(),
            new MinimumChargeRule()));
    }

    /**
     * Prices one booking. Each rule sees the total of the lines produced before
     * it, and every line it returns has to be in the rate card's currency.
     */
    public PriceQuote quote(PriceContext context) {
        Objects.requireNonNull(context, "context");
        List<LineItem> lineItems = new ArrayList<>();
        Money runningTotal = context.zero();
        for (PriceRule rule : rules) {
            List<LineItem> contributed = rule.apply(context, runningTotal);
            Objects.requireNonNull(contributed, "lines from rule " + rule.code());
            for (LineItem item : contributed) {
                runningTotal = runningTotal.plus(item.amount());
                lineItems.add(item);
            }
        }
        return new PriceQuote(lineItems, runningTotal);
    }

    /** Prices a room, a time, and a party size against a rate card. */
    public PriceQuote quote(Room room, TimeInterval interval, int attendees, RateCard rateCard) {
        return quote(PriceContext.of(room, interval, attendees, rateCard));
    }

    /** The codes of the rules, in the order they run. */
    public List<String> ruleCodes() {
        return rules.stream().map(PriceRule::code).toList();
    }
}
