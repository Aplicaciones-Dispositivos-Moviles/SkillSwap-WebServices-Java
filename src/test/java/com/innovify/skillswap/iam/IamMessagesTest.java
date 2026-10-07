package com.innovify.skillswap.iam;

import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Every IAM error has a message in each supported language. */
class IamMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyIamErrorHasAMessage(String languageTag) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        Locale locale = Locale.forLanguageTag(languageTag);

        for (IamError error : IamError.values()) {
            String code = ErrorCodes.of(error);
            String message = messages.getMessage(code, null, "MISSING", locale);
            assertThat(message).as("%s in %s", code, languageTag).isNotEqualTo("MISSING").isNotBlank();
        }
    }
}
