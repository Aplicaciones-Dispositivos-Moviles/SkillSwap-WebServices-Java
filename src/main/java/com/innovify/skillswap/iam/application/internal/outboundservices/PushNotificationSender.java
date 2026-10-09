package com.innovify.skillswap.iam.application.internal.outboundservices;

/**
 * Sends push notifications to a device, decoupling the application from the provider (Firebase Cloud Messaging).
 * Implementations never throw: every failure is reported as a {@link PushDeliveryResult}.
 */
public interface PushNotificationSender {

    PushDeliveryResult send(String deviceToken, PushNotification notification);
}
