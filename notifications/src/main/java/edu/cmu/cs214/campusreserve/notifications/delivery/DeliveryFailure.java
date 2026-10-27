package edu.cmu.cs214.campusreserve.notifications.delivery;

import edu.cmu.cs214.campusreserve.notifications.Notification;
import java.util.Objects;

/**
 * One message that did not get delivered.
 *
 * @param notificationId the message that failed
 * @param recipientUserId who it was for
 * @param attempts how many times delivery was tried before giving up
 * @param reason the message from the last failure
 */
public record DeliveryFailure(String notificationId, String recipientUserId, int attempts, String reason) {

    public DeliveryFailure {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(reason, "reason");
        if (attempts < 1) {
            throw new IllegalArgumentException("Attempts must be at least 1: " + attempts);
        }
    }

    /** Builds the record from the message and the failure that ended it. */
    public static DeliveryFailure of(Notification notification, int attempts, Throwable failure) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(failure, "failure");
        String reason = failure.getMessage() == null
            ? failure.getClass().getSimpleName()
            : failure.getMessage();
        return new DeliveryFailure(notification.id(), notification.recipientUserId(), attempts, reason);
    }

    /** Did this failure survive more than one attempt? */
    public boolean wasRetried() {
        return attempts > 1;
    }
}
