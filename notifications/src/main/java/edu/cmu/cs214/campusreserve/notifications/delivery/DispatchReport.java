package edu.cmu.cs214.campusreserve.notifications.delivery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What one dispatch run did.
 *
 * @param delivered how many messages the transport accepted
 * @param failures the messages it did not, in the order they were tried
 */
public record DispatchReport(int delivered, List<DeliveryFailure> failures) {

    public DispatchReport {
        Objects.requireNonNull(failures, "failures");
        if (delivered < 0) {
            throw new IllegalArgumentException("Delivered count cannot be negative: " + delivered);
        }
        failures = List.copyOf(failures);
    }

    /** How many messages failed. */
    public int failed() {
        return failures.size();
    }

    /** How many messages the run handled, delivered and failed together. */
    public int total() {
        return delivered + failed();
    }

    /** Did everything get through? */
    public boolean allDelivered() {
        return failures.isEmpty();
    }

    /** The failure for one message, if that message failed. */
    public Optional<DeliveryFailure> failureFor(String notificationId) {
        for (DeliveryFailure failure : failures) {
            if (failure.notificationId().equals(notificationId)) {
                return Optional.of(failure);
            }
        }
        return Optional.empty();
    }
}
