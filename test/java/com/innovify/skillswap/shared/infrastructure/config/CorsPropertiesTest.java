package com.innovify.skillswap.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void withoutOrigins_isEmpty() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(List.of("", "  ")).allowedOrigins()).isEmpty();
    }

    @Test
    void splitsCommaSeparatedValuesAndCleansThem() {
        var properties = new CorsProperties(
                Arrays.asList("https://a.com/", " https://b.com , https://A.com ", null, ""));

        assertThat(properties.allowedOrigins()).containsExactly("https://a.com", "https://b.com");
    }
}
