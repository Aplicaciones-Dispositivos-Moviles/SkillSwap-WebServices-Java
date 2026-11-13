package com.innovify.skillswap.credentialverification.infrastructure.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CloudinarySettingsTest {

    private static final String BASE_URL = "https://api.cloudinary.com";

    @Test
    void withAllValues_isCreated() {
        CloudinarySettings settings = new CloudinarySettings("demo", "key", "secret", BASE_URL);

        assertThat(settings.cloudName()).isEqualTo("demo");
        assertThat(settings.apiKey()).isEqualTo("key");
        assertThat(settings.apiSecret()).isEqualTo("secret");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void withoutCloudName_failsFast(String value) {
        assertThatThrownBy(() -> new CloudinarySettings(value, "key", "secret", BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOUDINARY_CLOUD_NAME");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void withoutApiKey_failsFast(String value) {
        assertThatThrownBy(() -> new CloudinarySettings("demo", value, "secret", BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOUDINARY_API_KEY");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void withoutApiSecret_failsFast(String value) {
        assertThatThrownBy(() -> new CloudinarySettings("demo", "key", value, BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOUDINARY_API_SECRET");
    }

    @Test
    void trailingSlashOfTheBaseUrl_isRemoved() {
        assertThat(new CloudinarySettings("demo", "key", "secret", BASE_URL + "/").apiBaseUrl())
                .isEqualTo(BASE_URL);
    }
}
