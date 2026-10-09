package com.innovify.skillswap.iam.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.infrastructure.email.EmailSettings;
import com.innovify.skillswap.iam.infrastructure.email.brevo.BrevoEmailSenderAdapter;
import com.innovify.skillswap.iam.infrastructure.email.logging.LoggingEmailSenderAdapter;
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
}
