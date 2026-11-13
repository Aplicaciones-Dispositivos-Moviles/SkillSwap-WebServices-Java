package com.innovify.skillswap.iam.infrastructure.push.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotification;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotificationSender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PushNotificationSender} on top of Firebase Cloud Messaging (HTTP v1 API, through the Firebase Admin SDK).
 * The tokens FCM reports as unregistered or invalid are answered as {@link PushDeliveryResult#INVALID_TOKEN}, so
 * they are forgotten.
 */
public class FirebasePushNotificationAdapter implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushNotificationAdapter.class);

    /** The name of the Firebase app of SkillSwap, so it never collides with a default app. */
    public static final String APP_NAME = "skillswap-push";

    /** Sends one message; {@link FirebaseMessaging#send(Message)} in production. */
    @FunctionalInterface
    public interface Messenger {
        String send(Message message) throws FirebaseMessagingException;
    }

    private final Messenger messenger;

    public FirebasePushNotificationAdapter(Messenger messenger) {
        this.messenger = messenger;
    }

    /**
     * Creates the adapter from the Base64 of the service account JSON.
     *
     * @throws IllegalArgumentException when the credentials cannot be read
     */
    public static FirebasePushNotificationAdapter fromCredentials(String credentialsBase64) {
        FirebaseApp app = initializeApp(credentialsBase64);
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
        return new FirebasePushNotificationAdapter(messaging::send);
    }

    static FirebaseApp initializeApp(String credentialsBase64) {
        GoogleCredentials credentials;
        try {
            byte[] json = Base64.getDecoder().decode(credentialsBase64);
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        } catch (IllegalArgumentException | IOException exception) {
            // The message of the exception never includes the credentials.
            throw new IllegalArgumentException(
                    "FIREBASE_CREDENTIALS_BASE64 is not the Base64 of a valid service account JSON.", exception);
        }

        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (existing.getName().equals(APP_NAME)) {
                existing.delete();
            }
        }
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build(), APP_NAME);
    }

    @Override
    public PushDeliveryResult send(String deviceToken, PushNotification notification) {
        Message message = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder()
                        .setTitle(notification.title())
                        .setBody(notification.body())
                        .build())
                .putAllData(notification.data())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .build();
        try {
            messenger.send(message);
            return PushDeliveryResult.SENT;
        } catch (FirebaseMessagingException exception) {
            PushDeliveryResult result = toResult(exception.getMessagingErrorCode());
            log.warn("FCM did not accept the push notification ({}): {}", exception.getMessagingErrorCode(),
                    exception.getMessage());
            return result;
        } catch (RuntimeException exception) {
            log.error("The push notification could not be sent through FCM", exception);
            return PushDeliveryResult.FAILED;
        }
    }

    /** UNREGISTERED (the app was uninstalled), INVALID_ARGUMENT (a malformed token) and SENDER_ID_MISMATCH. */
    public static PushDeliveryResult toResult(MessagingErrorCode code) {
        if (code == null) {
            return PushDeliveryResult.FAILED;
        }
        return switch (code) {
            case UNREGISTERED, INVALID_ARGUMENT, SENDER_ID_MISMATCH -> PushDeliveryResult.INVALID_TOKEN;
            default -> PushDeliveryResult.FAILED;
        };
    }
}
