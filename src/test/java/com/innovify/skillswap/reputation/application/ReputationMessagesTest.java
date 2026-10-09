package com.innovify.skillswap.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.application.fakes.TestMessages;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;

class ReputationMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyError_hasAMessageInEveryLanguage(String languageTag) {
        var source = TestMessages.source();
        Locale locale = Locale.forLanguageTag(languageTag);

        for (ReputationError error : ReputationError.values()) {
            if (error == ReputationError.NONE) {
                continue;
            }
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
        var source = TestMessages.source();

        assertThat(source.getMessage("NotReputationOwner", null, Locale.forLanguageTag("es-419")))
                .isNotEqualTo(source.getMessage("NotReputationOwner", null, Locale.US));
    }
}
