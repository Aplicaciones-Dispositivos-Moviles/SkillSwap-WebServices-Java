package com.innovify.skillswap.assessmentpeerreview.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;

class AssessmentPeerReviewMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyError_hasAMessageInEveryLanguage(String languageTag) {
        var source = TestMessages.source();
        Locale locale = Locale.forLanguageTag(languageTag);

        for (AssessmentPeerReviewError error : AssessmentPeerReviewError.values()) {
            if (error == AssessmentPeerReviewError.NONE) {
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

        assertThat(source.getMessage("NotAVerifier", null, Locale.forLanguageTag("es-419")))
                .isNotEqualTo(source.getMessage("NotAVerifier", null, Locale.US));
    }
}
