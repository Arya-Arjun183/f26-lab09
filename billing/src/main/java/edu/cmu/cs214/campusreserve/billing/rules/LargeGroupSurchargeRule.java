package edu.cmu.cs214.campusreserve.billing.rules;

import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.PriceRule;
import edu.cmu.cs214.campusreserve.billing.RateCard;
import java.util.List;

/**
 * Adds a percentage of the base charge for a party at or above the rate card's
 * large group threshold. Big groups cost more to turn around between bookings,
 * and the surcharge covers that.
 */
public final class LargeGroupSurchargeRule implements PriceRule {

    /** The code this rule stamps on its line. */
    public static final String CODE = "GROUP";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<LineItem> apply(PriceContext context, Money runningTotal) {
        RateCard card = context.rateCard();
        if (card.largeGroupSurchargePercent() == 0 || context.attendees() < card.largeGroupThreshold()) {
            return List.of();
        }
        Money amount = context.baseCharge().percentage(card.largeGroupSurchargePercent());
        if (amount.isZero()) {
            return List.of();
        }
        String description = "Large group surcharge " + card.largeGroupSurchargePercent()
            + " percent for " + context.attendees() + " attendees";
        return List.of(LineItem.of(CODE, description, amount));
    }
}
