package com.innovify.skillswap.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.infrastructure.email.EmailSettings;
import com.innovify.skillswap.iam.infrastructure.email.brevo.BrevoEmailSenderAdapter;
import com.innovify.skillswap.iam.infrastructure.email.logging.LoggingEmailSenderAdapter;
import com.innovify.skillswap.iam.infrastructure.push.FirebaseSettings;
import com.innovify.skillswap.iam.infrastructure.push.logging.LoggingPushNotificationAdapter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Which adapter is used depends only on the configuration, as with the payment gateway. */
class IamConfigTest {

    private final IamConfig config = new IamConfig();

    @Test
    void emailSender_withoutAnApiKey_onlyLogsTheEmails() {
        assertThat(config.emailSender(new EmailSettings(null, "no-reply@skillswap.pe", null, null, 5, 10)))
                .isInstanceOf(LoggingEmailSenderAdapter.class);
    }

    @Test
    void emailSender_withAnApiKeyButNoSender_onlyLogsTheEmails() {
        assertThat(config.emailSender(new EmailSettings("xkeysib-test", null, null, null, 5, 10)))
                .isInstanceOf(LoggingEmailSenderAdapter.class);
    }

    @Test
    void emailSender_withAnApiKeyAndASender_usesBrevo() {
        assertThat(config.emailSender(new EmailSettings("xkeysib-test", "no-reply@skillswap.pe", null, null, 5, 10)))
                .isInstanceOf(BrevoEmailSenderAdapter.class);
    }

    @Test
    void pushNotificationSender_withoutCredentials_onlyLogsTheNotifications() {
        assertThat(config.pushNotificationSender(new FirebaseSettings(null)))
                .isInstanceOf(LoggingPushNotificationAdapter.class);
    }

    @Test
    void pushNotificationSender_withInvalidCredentials_onlyLogsTheNotificationsInsteadOfStoppingTheApplication() {
        String invalid = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));

        assertThat(config.pushNotificationSender(new FirebaseSettings(invalid)))
                .isInstanceOf(LoggingPushNotificationAdapter.class);
    }
}
