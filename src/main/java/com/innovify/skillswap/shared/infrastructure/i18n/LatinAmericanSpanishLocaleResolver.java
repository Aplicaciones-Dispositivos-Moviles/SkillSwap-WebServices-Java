package com.innovify.skillswap.shared.infrastructure.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Maps any Spanish variant requested through Accept-Language (es, es-PE, es-MX, ...) to Latin American
 * Spanish (es-419), the project's secondary language. Anything else resolves to the default (English).
 */
public class LatinAmericanSpanishLocaleResolver implements LocaleResolver {

    public static final Locale LATIN_AMERICAN_SPANISH = Locale.forLanguageTag("es-419");
    public static final Locale DEFAULT_LOCALE = Locale.US;

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return DEFAULT_LOCALE;
        }
        try {
            return Locale.LanguageRange.parse(header).stream()
                    .sorted(Comparator.comparingDouble(Locale.LanguageRange::getWeight).reversed())
                    .findFirst()
                    .map(range -> isSpanish(range.getRange()) ? LATIN_AMERICAN_SPANISH : DEFAULT_LOCALE)
                    .orElse(DEFAULT_LOCALE);
        } catch (IllegalArgumentException invalidHeader) {
            return DEFAULT_LOCALE;
        }
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The locale is resolved from the Accept-Language header.");
    }

    private static boolean isSpanish(String range) {
        String lower = range.toLowerCase(Locale.ROOT);
        return lower.equals("es") || lower.startsWith("es-");
    }
}
