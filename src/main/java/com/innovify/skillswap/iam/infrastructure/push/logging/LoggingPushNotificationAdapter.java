package com.innovify.skillswap.iam.infrastructure.push.logging;

import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotification;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotificationSender;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PushNotificationSender} used when no Firebase credentials are configured (local development, tests,
 * demos): nothing is sent, the notification is written to the log instead.
 */
public class LoggingPushNotificationAdapter implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationAdapter.class);

    @Override
    public PushDeliveryResult send(String deviceToken, PushNotification notification) {
        log.info("Push notification NOT sent (FIREBASE_CREDENTIALS_BASE64 is not set). Device: {} | {}: {} | {}",
                new DeviceToken(deviceToken).abbreviated(), notification.title(), notification.body(),
                notification.data());
        return PushDeliveryResult.SENT;
    }
}
