package edu.cmu.cs214.campusreserve.billing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a booking costs and how the price was reached: the line items in the
 * order the rules produced them, plus their sum.
 *
 * <p>A quote is a value. An invoice keeps the quote it was issued with, so the
 * price a user was told stays readable long after the rate card moves on.
 *
 * @param lineItems the lines in pipeline order, at least one
 * @param total the sum of the lines
 */
public record PriceQuote(List<LineItem> lineItems, Money total) {

    public PriceQuote {
        Objects.requireNonNull(lineItems, "lineItems");
        Objects.requireNonNull(total, "total");
        lineItems = List.copyOf(lineItems);
        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("A quote needs at least one line item");
        }
        Money sum = Money.zero(total.currency());
        for (LineItem item : lineItems) {
            sum = sum.plus(item.amount());
        }
        if (!sum.equals(total)) {
            throw new IllegalArgumentException(
                "Total " + total.format() + " does not match the lines, which sum to " + sum.format());
        }
    }

    /** The line carrying that code, if the pipeline produced one. */
    public Optional<LineItem> lineItem(String code) {
        return lineItems.stream().filter(item -> item.code().equals(code)).findFirst();
    }

    /** Did the pipeline produce a line with that code? */
    public boolean hasLineItem(String code) {
        return lineItem(code).isPresent();
    }

    /** The codes of the lines, in pipeline order. */
    public List<String> codes() {
        return lineItems.stream().map(LineItem::code).toList();
    }

    /** How many lines the quote carries. */
    public int size() {
        return lineItems.size();
    }

    /** The currency of the quote. */
    public String currency() {
        return total.currency();
    }

    /**
     * The quote as several lines of text, one per line item and a last line for
     * the total. Used wherever a quote is shown to a person.
     */
    public String format() {
        StringBuilder text = new StringBuilder();
        for (LineItem item : lineItems) {
            text.append(item.code())
                .append("  ")
                .append(item.description())
                .append("  ")
                .append(item.amount().format())
                .append(System.lineSeparator());
        }
        return text.append("TOTAL  ").append(total.format()).toString();
    }
}
