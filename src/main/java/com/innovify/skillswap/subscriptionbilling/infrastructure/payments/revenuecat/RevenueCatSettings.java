package com.innovify.skillswap.subscriptionbilling.infrastructure.payments.revenuecat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * RevenueCat settings from {@code revenuecat.*} (environment: REVENUECAT_API_KEY, REVENUECAT_WEBHOOK_AUTH,
 * REVENUECAT_ENTITLEMENT_ID...). Without an API key the payments are simulated
 * ({@code SimulatedPaymentGatewayAdapter}), so local development and the tests need no RevenueCat account. Never
 * commit a real key, and never log these values.
 *
 * @param apiKey               secret API key (sk_...) of the RevenueCat project, sent as a Bearer token
 * @param webhookAuthorization the exact value RevenueCat sends in the Authorization header of the webhook; when
 *                             blank every notification is rejected
 * @param entitlementId        the entitlement that grants the paid plan
 * @param acceptSandboxEvents  whether the notifications of test purchases (environment SANDBOX) are applied
 * @param connectTimeoutSeconds maximum time to open the connection
 * @param readTimeoutSeconds   maximum time to wait for the answer
 * @param baseUrl              base URL of the REST API v1; only tests change it
 */
@ConfigurationProperties(prefix = "revenuecat")
public record RevenueCatSettings(
        String apiKey,
        String webhookAuthorization,
        @DefaultValue("premium") String entitlementId,
        @DefaultValue("true") boolean acceptSandboxEvents,
        @DefaultValue("5") int connectTimeoutSeconds,
        @DefaultValue("10") int readTimeoutSeconds,
        @DefaultValue("https://api.revenuecat.com/v1/") String baseUrl) {

    public RevenueCatSettings {
        apiKey = blankToNull(apiKey);
        webhookAuthorization = blankToNull(webhookAuthorization);
        entitlementId = entitlementId == null || entitlementId.isBlank() ? "premium" : entitlementId.strip();
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("The RevenueCat timeouts must be greater than zero.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("The setting revenuecat.base-url is required.");
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /** Whether the real gateway can be used. */
    public boolean hasApiKey() {
        return apiKey != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Keeps the secrets out of the logs and of any error message that prints the settings. */
    @Override
    public String toString() {
        return "RevenueCatSettings[apiKey=" + (hasApiKey() ? "***" : "none")
                + ", webhookAuthorization=" + (webhookAuthorization == null ? "none" : "***")
                + ", entitlementId=" + entitlementId + ", acceptSandboxEvents=" + acceptSandboxEvents
                + ", baseUrl=" + baseUrl + "]";
    }
}
