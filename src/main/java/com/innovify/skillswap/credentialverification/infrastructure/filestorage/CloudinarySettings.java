package com.innovify.skillswap.credentialverification.infrastructure.filestorage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cloudinary credentials from {@code cloudinary.*} (environment: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY,
 * CLOUDINARY_API_SECRET). The three are required: the application does not start without them. Never commit
 * real values.
 *
 * @param cloudName cloud name
 * @param apiKey    API key
 * @param apiSecret API secret, used only to sign requests
 * @param apiBaseUrl base URL of the Cloudinary API; only tests change it
 */
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinarySettings(String cloudName, String apiKey, String apiSecret,
                                 @DefaultValue("https://api.cloudinary.com") String apiBaseUrl) {

    public CloudinarySettings {
        requireValue(cloudName, "cloudinary.cloud-name (CLOUDINARY_CLOUD_NAME)");
        requireValue(apiKey, "cloudinary.api-key (CLOUDINARY_API_KEY)");
        requireValue(apiSecret, "cloudinary.api-secret (CLOUDINARY_API_SECRET)");
        requireValue(apiBaseUrl, "cloudinary.api-base-url");
        apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The setting " + name + " is required.");
        }
    }
}
