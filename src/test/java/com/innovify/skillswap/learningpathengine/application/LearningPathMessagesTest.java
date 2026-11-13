package com.innovify.skillswap.learningpathengine.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

class LearningPathMessagesTest {

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyError_hasAMessageInEveryLanguage(String languageTag) {
        var source = messages();
        Locale locale = Locale.forLanguageTag(languageTag);

        for (LearningPathError error : LearningPathError.values()) {
            String code = ErrorCodes.of(error);
            try {
                assertThat(source.getMessage(code, null, locale)).as(code).isNotBlank();
            } catch (NoSuchMessageException e) {
                throw new AssertionError("Missing message '" + code + "' for " + languageTag, e);
            }
        }
    }

    @Test
    void spanishMessages_areNotTheEnglishOnes() {
        var source = messages();

        assertThat(source.getMessage("NodeLocked", null, Locale.forLanguageTag("es-419")))
                .isNotEqualTo(source.getMessage("NodeLocked", null, Locale.US));
    }
}
