package com.innovify.skillswap.shared.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LatinAmericanSpanishLocaleResolverTest {

    private final LatinAmericanSpanishLocaleResolver resolver = new LatinAmericanSpanishLocaleResolver();

    private Locale resolve(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        return resolver.resolveLocale(request);
    }

    @Test
    void anySpanishVariant_resolvesToLatinAmericanSpanish() {
        assertThat(resolve("es")).isEqualTo(LatinAmericanSpanishLocaleResolver.LATIN_AMERICAN_SPANISH);
        assertThat(resolve("es-PE,es;q=0.9,en;q=0.8")).isEqualTo(
                LatinAmericanSpanishLocaleResolver.LATIN_AMERICAN_SPANISH);
    }

    @Test
    void theHighestWeightWins() {
        assertThat(resolve("en;q=0.5,es;q=1.0")).isEqualTo(
                LatinAmericanSpanishLocaleResolver.LATIN_AMERICAN_SPANISH);
        assertThat(resolve("es;q=0.4,en;q=0.9")).isEqualTo(LatinAmericanSpanishLocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    void otherLanguagesMissingOrInvalidHeaders_resolveToTheDefault() {
        assertThat(resolve("en-US")).isEqualTo(LatinAmericanSpanishLocaleResolver.DEFAULT_LOCALE);
        assertThat(resolve("fr")).isEqualTo(LatinAmericanSpanishLocaleResolver.DEFAULT_LOCALE);
        assertThat(resolve(null)).isEqualTo(LatinAmericanSpanishLocaleResolver.DEFAULT_LOCALE);
        assertThat(resolve(";;;")).isEqualTo(LatinAmericanSpanishLocaleResolver.DEFAULT_LOCALE);
    }
}
