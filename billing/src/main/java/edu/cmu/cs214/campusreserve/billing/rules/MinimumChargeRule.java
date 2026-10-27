package edu.cmu.cs214.campusreserve.billing.rules;

import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.PriceRule;
import java.util.List;

/**
 * Tops a quote up to the rate card's floor when the lines before it come to
 * less.
 *
 * <p>This is the only rule that reads the running total, and it is the reason
 * the pipeline has a fixed order: run it before the discount and it would top a
 * quote up to the floor and then hand the discount something to cut into,
 * leaving a settled price below the minimum. {@code PriceCalculator} puts it
 * last for that reason.
 */
public final class MinimumChargeRule implements PriceRule {

    /** The code this rule stamps on its line. */
    public static final String CODE = "MIN";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<LineItem> apply(PriceContext context, Money runningTotal) {
        Money floor = context.rateCard().minimumCharge();
        if (!runningTotal.isLessThan(floor)) {
            return List.of();
        }
        Money topUp = floor.minus(runningTotal);
        String description = "Minimum charge top-up to " + floor.format();
        return List.of(LineItem.of(CODE, description, topUp));
    }
}
