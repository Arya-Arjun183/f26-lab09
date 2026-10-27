package edu.cmu.cs214.campusreserve.billing.rules;

import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.PriceRule;
import edu.cmu.cs214.campusreserve.billing.RateCard;
import java.util.List;

/**
 * Takes a percentage of the base charge off a booking that runs at or past the
 * rate card's long booking threshold. This is the one rule that contributes a
 * negative amount, so a quote that carries a {@code LONG} line has a credit in
 * it.
 */
public final class LongBookingDiscountRule implements PriceRule {

    /** The code this rule stamps on its line. */
    public static final String CODE = "LONG";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<LineItem> apply(PriceContext context, Money runningTotal) {
        RateCard card = context.rateCard();
        if (card.longBookingDiscountPercent() == 0 || context.minutes() < card.longBookingMinutes()) {
            return List.of();
        }
        Money credit = context.baseCharge().percentage(card.longBookingDiscountPercent());
        if (credit.isZero()) {
            return List.of();
        }
        String description = "Long booking discount " + card.longBookingDiscountPercent()
            + " percent on " + context.minutes() + " min";
        return List.of(LineItem.of(CODE, description, credit.negated()));
    }
}
