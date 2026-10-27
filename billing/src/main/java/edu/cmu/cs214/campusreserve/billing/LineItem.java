package edu.cmu.cs214.campusreserve.billing;

import edu.cmu.cs214.campusreserve.core.domain.Identifiers;
import java.util.Objects;

/**
 * One line of a quote: what was charged, why, and how much.
 *
 * <p>The code is the stable handle a caller matches on, for example
 * {@code PEAK}. The description is written for a person reading an invoice and
 * may change without breaking anything. A negative amount is a credit, which is
 * how a discount shows up.
 *
 * @param code short uppercase handle for the rule that produced the line
 * @param description one line of plain text for the reader of the invoice
 * @param amount what the line adds to the total, negative for a credit
 */
public record LineItem(String code, String description, Money amount) {

    public LineItem {
        code = Identifiers.require(code, "line item code");
        description = Identifiers.requireText(description, "line item description");
        Objects.requireNonNull(amount, "amount");
    }

    /** Builds a line item. */
    public static LineItem of(String code, String description, Money amount) {
        return new LineItem(code, description, amount);
    }

    /** Does this line take money off the total? */
    public boolean isCredit() {
        return amount.isNegative();
    }

    @Override
    public String toString() {
        return code + " " + description + " " + amount.format();
    }
}
