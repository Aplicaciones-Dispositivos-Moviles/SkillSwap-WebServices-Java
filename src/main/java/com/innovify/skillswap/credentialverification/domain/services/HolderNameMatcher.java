package com.innovify.skillswap.credentialverification.domain.services;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Domain service: decides whether the holder read from a certificate is the student who registered it. Names are
 * written in many ways, so both are normalized before comparing them: accents, case and punctuation are ignored,
 * the order of the words does not matter ("PÉREZ GARCÍA, Ana" is "Ana Pérez García"), the particles of compound
 * names (de, del, la...) are skipped, and an initial matches a word that starts with it ("Ana M. Pérez").
 *
 * <p>Certificates often leave out a middle name or the second surname, so the shorter name only has to be
 * contained in the longer one, as long as it has at least two words with one of them written in full: a lone
 * first name ("Ana") does not identify anybody.
 */
public final class HolderNameMatcher {

    private static final Set<String> PARTICLES = Set.of("de", "del", "la", "las", "los", "y", "e", "da", "das",
            "do", "dos", "van", "von", "der", "di", "le", "san");

    /**
     * Whether both names belong to the same person. When one of them is missing there is nothing to compare, so
     * they are not reported as different.
     */
    public boolean matches(String certificateHolder, String registeredName) {
        List<String> holder = tokens(certificateHolder);
        List<String> registered = tokens(registeredName);
        if (holder.isEmpty() || registered.isEmpty()) {
            return true;
        }

        List<String> shorter = holder.size() <= registered.size() ? holder : registered;
        List<String> longer = shorter == holder ? registered : holder;
        if (shorter.size() < Math.min(2, longer.size())) {
            return false;
        }

        // Whole words are paired first, so an initial cannot take the word another whole word needs.
        List<String> pending = new ArrayList<>(shorter);
        pending.sort(Comparator.comparing(HolderNameMatcher::isInitial));
        List<String> available = new ArrayList<>(longer);
        boolean wholeWordMatched = false;
        for (String token : pending) {
            String partner = findPartner(token, available);
            if (partner == null) {
                return false;
            }
            available.remove(partner);
            wholeWordMatched |= !isInitial(token) && !isInitial(partner);
        }
        return wholeWordMatched;
    }

    /** Whether the name has something to compare once normalized. */
    public boolean isComparable(String name) {
        return !tokens(name).isEmpty();
    }

    private static String findPartner(String token, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(token)) {
                return candidate;
            }
        }
        for (String candidate : candidates) {
            boolean initialOfCandidate = isInitial(token) && candidate.startsWith(token);
            boolean candidateIsInitial = isInitial(candidate) && token.startsWith(candidate);
            if (initialOfCandidate || candidateIsInitial) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isInitial(String token) {
        return token.length() == 1;
    }

    /** Lowercase words without accents or punctuation, leaving out the particles unless that empties the name. */
    static List<String> tokens(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
        if (plain.isEmpty()) {
            return List.of();
        }

        List<String> words = Arrays.asList(plain.split(" "));
        List<String> meaningful = words.stream().filter(word -> !PARTICLES.contains(word)).toList();
        return meaningful.isEmpty() ? List.copyOf(words) : meaningful;
    }
}
