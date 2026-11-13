package com.innovify.skillswap.iam.application.internal.outboundservices;

import java.util.Map;
import java.util.Objects;

/**
 * A push notification for one device.
 *
 * @param title the title shown by the system
 * @param body  the text shown by the system
 * @param data  key-value pairs the app reads when the notification is opened (e.g. the certificate id)
 */
public record PushNotification(String title, String body, Map<String, String> data) {

    public PushNotification {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
