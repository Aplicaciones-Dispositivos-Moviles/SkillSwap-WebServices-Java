package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes text so that keywords and goals are compared regardless of case, accents or punctuation:
 * lowercase, no diacritics, and every character other than letters, digits, '#' and '+' becomes a space (so
 * "C#" and "ASP.NET" keep meaning).
 */
final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{Mn}+");
    private static final Pattern NON_SEARCHABLE = Pattern.compile("[^a-z0-9#+]+");

    private TextNormalizer() {
    }

    static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutAccents = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_SEARCHABLE.matcher(withoutAccents).replaceAll(" ").strip();
    }
}
