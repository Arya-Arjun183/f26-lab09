package edu.cmu.cs214.campusreserve.billing.rules;

import edu.cmu.cs214.campusreserve.billing.LineItem;
import edu.cmu.cs214.campusreserve.billing.Money;
import edu.cmu.cs214.campusreserve.billing.PriceContext;
import edu.cmu.cs214.campusreserve.billing.PriceRule;
import java.util.List;

/**
 * Charges the booked time at the room kind's hourly rate.
 *
 * <p>Time is billed by the minute, not rounded up to the hour: a 90 minute
 * booking of a room at USD 5.00 an hour costs USD 7.50, and a 20 minute booking
 * of the same room costs USD 1.67. Every other rule in the pipeline is a
 * percentage of this line or a floor under the total.
 */
public final class BaseChargeRule implements PriceRule {

    /** The code this rule stamps on its line. */
    public static final String CODE = "BASE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<LineItem> apply(PriceContext context, Money runningTotal) {
        Money amount = context.baseCharge();
        String description = context.minutes() + " min in " + context.room().label()
            + " at " + context.hourlyRate().format() + " per hour";
        return List.of(LineItem.of(CODE, description, amount));
    }
}
