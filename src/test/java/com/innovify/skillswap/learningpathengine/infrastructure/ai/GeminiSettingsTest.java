package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GeminiSettingsTest {

    private static GeminiSettings settings(String apiKey, String model, List<String> fallbacks) {
        return new GeminiSettings(apiKey, model, fallbacks, "low", 30, 60, 1, 1000, "https://example.test/v1/");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void apiKey_isRequired(String apiKey) {
        assertThatThrownBy(() -> settings(apiKey, "model", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void model_isRequired() {
        assertThatThrownBy(() -> settings("key", " ", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseUrl_alwaysEndsWithASlash() {
        GeminiSettings withoutSlash =
                new GeminiSettings("key", "model", null, null, 30, 60, 1, 1000, "https://example.test/v1");

        assertThat(withoutSlash.baseUrl()).isEqualTo("https://example.test/v1/");
    }

    @Test
    void timeouts_mustBePositive() {
        assertThatThrownBy(() -> new GeminiSettings("key", "m", null, null, 0, 60, 1, 1000, "https://x.test/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeminiSettings("key", "m", null, null, 30, 0, 1, 1000, "https://x.test/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retries_cannotBeNegative() {
        assertThatThrownBy(() -> new GeminiSettings("key", "m", null, null, 30, 60, -1, 1000, "https://x.test/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeminiSettings("key", "m", null, null, 30, 60, 1, -1, "https://x.test/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankThinkingLevel_isTreatedAsNotConfigured() {
        GeminiSettings blank = new GeminiSettings("key", "m", null, "  ", 30, 60, 1, 1000, "https://x.test/");

        assertThat(blank.thinkingLevel()).isNull();
        assertThat(blank.fallbackModels()).isEmpty();
    }

    @Test
    void modelChain_putsTheMainModelFirstAndDropsBlanksAndRepeatsIgnoringCase() {
        GeminiSettings settings = settings("key", "main", Arrays.asList("MAIN", " ", "fallback-1", "Fallback-1", "fallback-2"));

        assertThat(settings.modelChain()).containsExactly("main", "fallback-1", "fallback-2");
    }
}
