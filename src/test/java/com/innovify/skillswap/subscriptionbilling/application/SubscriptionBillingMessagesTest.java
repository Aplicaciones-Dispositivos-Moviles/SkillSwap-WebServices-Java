package com.innovify.skillswap.subscriptionbilling.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import com.innovify.skillswap.subscriptionbilling.application.fakes.TestMessages;
import com.innovify.skillswap.subscriptionbilling.domain.model.SubscriptionBillingError;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;

class SubscriptionBillingMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyError_hasAMessageInEveryLanguage(String languageTag) {
        var source = TestMessages.source();
        Locale locale = Locale.forLanguageTag(languageTag);

        for (SubscriptionBillingError error : SubscriptionBillingError.values()) {
            if (error == SubscriptionBillingError.NONE) {
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

        assertThat(source.getMessage("PurchaseNotVerified", null, Locale.forLanguageTag("es-419")))
                .isNotEqualTo(source.getMessage("PurchaseNotVerified", null, Locale.US));
    }
}
