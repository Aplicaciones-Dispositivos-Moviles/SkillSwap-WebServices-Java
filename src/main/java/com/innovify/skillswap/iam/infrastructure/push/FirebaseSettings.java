package com.innovify.skillswap.iam.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase settings from {@code firebase.*} (environment: FIREBASE_CREDENTIALS_BASE64). Without credentials the
 * push notifications are only written to the log ({@code LoggingPushNotificationAdapter}), so local development and
 * the tests need no Firebase project. Never commit real credentials, and never log them.
 *
 * @param credentialsBase64 the service account JSON of the Firebase project, encoded in Base64 (one line, so it
 *                          fits in an environment variable)
 */
@ConfigurationProperties(prefix = "firebase")
public record FirebaseSettings(String credentialsBase64) {

    public FirebaseSettings {
        credentialsBase64 = credentialsBase64 == null || credentialsBase64.isBlank()
                ? null
                : credentialsBase64.replaceAll("\\s", "");
    }

    public boolean hasCredentials() {
        return credentialsBase64 != null;
    }

    /** Keeps the credentials out of the logs. */
    @Override
    public String toString() {
        return "FirebaseSettings[credentialsBase64=" + (hasCredentials() ? "***" : "none") + "]";
    }
}
