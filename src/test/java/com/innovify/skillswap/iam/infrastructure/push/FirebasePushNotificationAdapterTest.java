package com.innovify.skillswap.iam.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotification;
import com.innovify.skillswap.iam.infrastructure.push.firebase.FirebasePushNotificationAdapter;
import com.innovify.skillswap.iam.infrastructure.push.logging.LoggingPushNotificationAdapter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The FCM adapter without network: the messenger is replaced, and the credentials are a throwaway key. */
class FirebasePushNotificationAdapterTest {

    private static final PushNotification NOTIFICATION = new PushNotification("Título", "Cuerpo",
            Map.of("certificateId", "3"));

    @AfterEach
    void deleteTheFirebaseApp() {
        FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(FirebasePushNotificationAdapter.APP_NAME))
                .forEach(FirebaseApp::delete);
    }

    @Test
    void send_handsTheMessageToFcm() {
        List<Message> messages = new ArrayList<>();
        var adapter = new FirebasePushNotificationAdapter(message -> {
            messages.add(message);
            return "projects/skillswap/messages/1";
        });

        assertThat(adapter.send("device-token", NOTIFICATION)).isEqualTo(PushDeliveryResult.SENT);
        assertThat(messages).hasSize(1);
    }

    @Test
    void send_whenFcmReportsTheTokenUnregistered_answersInvalidToken() {
        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        var adapter = new FirebasePushNotificationAdapter(message -> {
            throw unregistered;
        });

        assertThat(adapter.send("device-token", NOTIFICATION)).isEqualTo(PushDeliveryResult.INVALID_TOKEN);
    }

    @Test
    void send_whenSomethingUnexpectedFails_answersFailed() {
        var adapter = new FirebasePushNotificationAdapter(message -> {
            throw new IllegalStateException("network down");
        });

        assertThat(adapter.send("device-token", NOTIFICATION)).isEqualTo(PushDeliveryResult.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = {"UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH"})
    void toResult_ofATokenError_isInvalidToken(MessagingErrorCode code) {
        assertThat(FirebasePushNotificationAdapter.toResult(code)).isEqualTo(PushDeliveryResult.INVALID_TOKEN);
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = {"QUOTA_EXCEEDED", "UNAVAILABLE", "INTERNAL",
            "THIRD_PARTY_AUTH_ERROR"})
    void toResult_ofATemporaryError_isFailed(MessagingErrorCode code) {
        assertThat(FirebasePushNotificationAdapter.toResult(code)).isEqualTo(PushDeliveryResult.FAILED);
        assertThat(FirebasePushNotificationAdapter.toResult(null)).isEqualTo(PushDeliveryResult.FAILED);
    }

    @Test
    void fromCredentials_withAServiceAccount_initializesTheFirebaseApp() throws Exception {
        var adapter = FirebasePushNotificationAdapter.fromCredentials(serviceAccountBase64());

        assertThat(adapter).isNotNull();
        assertThat(FirebaseApp.getApps()).anyMatch(app -> app.getName().equals(FirebasePushNotificationAdapter.APP_NAME));
        // A second initialization (e.g. a context refresh) replaces the app instead of failing.
        assertThat(FirebasePushNotificationAdapter.fromCredentials(serviceAccountBase64())).isNotNull();
    }

    /**
     * The whole send path of the SDK runs (no class is missing from the trimmed dependencies), and an unreachable
     * Google answers FAILED instead of throwing. The OAuth token endpoint is a closed local port: no network.
     */
    @Test
    void fromCredentials_whenGoogleCannotBeReached_answersFailed() throws Exception {
        var adapter = FirebasePushNotificationAdapter.fromCredentials(
                serviceAccountBase64("http://127.0.0.1:9/token"));

        assertThat(adapter.send("device-token", NOTIFICATION)).isEqualTo(PushDeliveryResult.FAILED);
    }

    @Test
    void fromCredentials_withSomethingElse_failsWithoutEchoingIt() {
        String notJson = Base64.getEncoder().encodeToString("secret-but-not-json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> FirebasePushNotificationAdapter.fromCredentials(notJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-but-not-json");
        assertThatThrownBy(() -> FirebasePushNotificationAdapter.fromCredentials("%%% not base64 %%%"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loggingAdapter_onlyLogsAndAnswersSent() {
        assertThat(new LoggingPushNotificationAdapter().send("device-token-123", NOTIFICATION))
                .isEqualTo(PushDeliveryResult.SENT);
    }

    @Test
    void settings_blankMeansNotConfiguredAndNeverPrintTheCredentials() {
        assertThat(new FirebaseSettings("  ").hasCredentials()).isFalse();
        FirebaseSettings settings = new FirebaseSettings("ZXlK\nc2VjcmV0 ");
        assertThat(settings.credentialsBase64()).isEqualTo("ZXlKc2VjcmV0");
        assertThat(settings.toString()).doesNotContain("ZXlK");
    }

    /** A service account JSON with a key generated for the test (it is not valid for any project). */
    static String serviceAccountBase64() throws Exception {
        return serviceAccountBase64("https://oauth2.googleapis.com/token");
    }

    static String serviceAccountBase64(String tokenUri) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = """
                {
                  "type": "service_account",
                  "project_id": "skillswap-test",
                  "private_key_id": "test-key-id",
                  "private_key": "%s",
                  "client_email": "firebase-adminsdk@skillswap-test.iam.gserviceaccount.com",
                  "client_id": "1234567890",
                  "token_uri": "%s"
                }
                """.formatted(pem.replace("\n", "\\n"), tokenUri);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
