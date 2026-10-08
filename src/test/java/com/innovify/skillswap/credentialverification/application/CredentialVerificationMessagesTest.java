package com.innovify.skillswap.credentialverification.application;

import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialVerificationMessagesTest {

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

        for (CredentialVerificationError error : CredentialVerificationError.values()) {
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

        assertThat(source.getMessage("FileTooLarge", null, Locale.forLanguageTag("es-419")))
                .isNotEqualTo(source.getMessage("FileTooLarge", null, Locale.US));
    }
}
