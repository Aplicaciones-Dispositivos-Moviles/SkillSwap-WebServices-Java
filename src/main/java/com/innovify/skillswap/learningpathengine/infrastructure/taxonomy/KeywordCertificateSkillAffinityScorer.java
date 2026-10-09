package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateContent;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores the affinity between a certificate and a skill with the keywords of the skill catalog, matched as whole
 * words or phrases regardless of case and accents (the same rule as {@link KeywordSkillTaxonomyMatcher}). The
 * course name weighs more than the rest of the text read by the OCR; the scale is documented in
 * {@link SkillAffinity}. It is deterministic and needs no external service, so it can run on every read of a
 * path.
 */
public class KeywordCertificateSkillAffinityScorer implements CertificateSkillAffinityScorer {

    static final double COURSE_NAME_MATCH = 1.0;
    static final double REPEATED_TEXT_MATCH = 0.75;
    static final double SINGLE_TEXT_MATCH = 0.5;
    static final double NO_MATCH = 0.0;

    private final Map<String, List<String>> paddedKeywordsByTag = new HashMap<>();

    public KeywordCertificateSkillAffinityScorer(SkillCatalog catalog) {
        for (SkillDefinition skill : catalog.skills()) {
            paddedKeywordsByTag.put(skill.tag(), skill.keywords().stream()
                    .map(TextNormalizer::normalize)
                    .filter(keyword -> !keyword.isEmpty())
                    .map(keyword -> " " + keyword + " ")
                    .distinct()
                    .toList());
        }
    }

    @Override
    public List<SkillAffinity> score(CertificateContent certificate, Collection<String> skillTags) {
        String courseName = padded(certificate == null ? null : certificate.courseName());
        String ocrText = padded(certificate == null ? null : certificate.ocrText());

        return skillTags.stream()
                .map(tag -> new SkillAffinity(tag, score(paddedKeywordsByTag.getOrDefault(tag, List.of()),
                        courseName, ocrText)))
                .toList();
    }

    private static double score(List<String> keywords, String courseName, String ocrText) {
        if (keywords.stream().anyMatch(courseName::contains)) {
            return COURSE_NAME_MATCH;
        }
        long mentions = keywords.stream().filter(ocrText::contains).count();
        if (mentions >= 2) {
            return REPEATED_TEXT_MATCH;
        }
        return mentions == 1 ? SINGLE_TEXT_MATCH : NO_MATCH;
    }

    private static String padded(String text) {
        String normalized = TextNormalizer.normalize(text);
        return normalized.isEmpty() ? "" : " " + normalized + " ";
    }
}
