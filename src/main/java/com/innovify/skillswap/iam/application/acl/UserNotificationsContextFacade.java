package com.innovify.skillswap.iam.application.acl;

import java.util.Map;

/**
 * Anti-corruption facade through which other bounded contexts notify a user on their mobile device, without
 * depending on the {@code User} aggregate, its device token or the push provider.
 */
public interface UserNotificationsContextFacade {

    /**
     * Sends a push notification to the device the user registered. Nothing is sent when the user has no device
     * token (the notification permission was not granted). Never throws.
     *
     * @param data key-value pairs for the app (may be empty)
     */
    PushNotificationOutcome sendPushNotification(int userId, String title, String body, Map<String, String> data);
}
