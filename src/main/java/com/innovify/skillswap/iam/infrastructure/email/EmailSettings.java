package com.innovify.skillswap.iam.infrastructure.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Transactional email settings from {@code email.*} (environment: BREVO_API_KEY, EMAIL_SENDER_ADDRESS,
 * EMAIL_SENDER_NAME). The emails go through the Brevo HTTP API because Render's free plan blocks outbound SMTP.
 * Without an API key (or without a sender address) the emails are only written to the log
 * ({@code LoggingEmailSenderAdapter}), so local development and the tests need no Brevo account. Never commit a
 * real key, and never log it.
 *
 * @param brevoApiKey           API key (xkeysib-...) of the Brevo account, sent in the {@code api-key} header
 * @param senderAddress         the sender address, verified in Brevo
 * @param senderName            the sender name shown by the mail client
 * @param brevoBaseUrl          base URL of the Brevo API v3; only tests change it
 * @param connectTimeoutSeconds maximum time to open the connection
 * @param readTimeoutSeconds    maximum time to wait for the answer
 */
@ConfigurationProperties(prefix = "email")
public record EmailSettings(
        String brevoApiKey,
        String senderAddress,
        @DefaultValue("SkillSwap") String senderName,
        @DefaultValue("https://api.brevo.com/v3/") String brevoBaseUrl,
        @DefaultValue("5") int connectTimeoutSeconds,
        @DefaultValue("10") int readTimeoutSeconds) {

    public static final String DEFAULT_SENDER_NAME = "SkillSwap";

    public EmailSettings {
        brevoApiKey = blankToNull(brevoApiKey);
        senderAddress = blankToNull(senderAddress);
        senderName = senderName == null || senderName.isBlank() ? DEFAULT_SENDER_NAME : senderName.strip();
        if (brevoBaseUrl == null || brevoBaseUrl.isBlank()) {
            brevoBaseUrl = "https://api.brevo.com/v3/";
        }
        brevoBaseUrl = brevoBaseUrl.endsWith("/") ? brevoBaseUrl : brevoBaseUrl + "/";
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("The email timeouts must be greater than zero.");
        }
    }

    public boolean hasBrevoApiKey() {
        return brevoApiKey != null;
    }

    public boolean hasSenderAddress() {
        return senderAddress != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Keeps the key out of the logs and of any error message that prints the settings. */
    @Override
    public String toString() {
        return "EmailSettings[brevoApiKey=" + (hasBrevoApiKey() ? "***" : "none") + ", senderAddress="
                + senderAddress + ", senderName=" + senderName + ", brevoBaseUrl=" + brevoBaseUrl + "]";
    }
}
