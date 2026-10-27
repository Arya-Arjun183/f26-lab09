package edu.cmu.cs214.campusreserve.billing.rules;

import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.PriceRule;
import edu.cmu.cs214.campusreserve.billing.RateCard;
import java.util.List;

/**
 * Adds a percentage of the base charge when a booking starts inside the peak
 * window on the rate card.
 *
 * <p>The test is on the start time only. A booking that begins before the window
 * opens pays nothing extra even if it runs well into the busy hours, which keeps
 * the surcharge something a user can work out before booking.
 */
public final class PeakHourSurchargeRule implements PriceRule {

    /** The code this rule stamps on its line. */
    public static final String CODE = "PEAK";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<LineItem> apply(PriceContext context, Money runningTotal) {
        RateCard card = context.rateCard();
        if (card.peakSurchargePercent() == 0 || !card.isPeakStart(context.startMinuteOfDayUtc())) {
            return List.of();
        }
        Money amount = context.baseCharge().percentage(card.peakSurchargePercent());
        if (amount.isZero()) {
            return List.of();
        }
        String description = "Peak window surcharge " + card.peakSurchargePercent() + " percent";
        return List.of(LineItem.of(CODE, description, amount));
    }
}
