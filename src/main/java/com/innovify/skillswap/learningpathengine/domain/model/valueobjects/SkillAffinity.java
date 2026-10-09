package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * How much the content of a certificate corresponds to a skill of the taxonomy, from 0 (nothing in common) to 1
 * (the certificate is about that skill). A certificate covers a skill when the affinity reaches
 * {@link #COVERAGE_THRESHOLD}.
 *
 * <p>The scale used by the keyword scorer, documented so the threshold can be reasoned about:
 * <ul>
 *   <li>1.0: the course name of the certificate mentions the skill (one of its keywords).</li>
 *   <li>0.75: the course name does not, but the full text read by the OCR mentions two or more different
 *   keywords of the skill.</li>
 *   <li>0.5: the full text mentions a single keyword: an incidental mention, not enough.</li>
 *   <li>0.0: no keyword of the skill appears.</li>
 * </ul>
 * With the threshold at 0.7 a certificate covers a skill when its course is about the skill, or when its full
 * text refers to the skill repeatedly.
 *
 * @param skillTag the skill of the taxonomy
 * @param score    the affinity, between 0 and 1
 */
public record SkillAffinity(String skillTag, double score) {

    public static final double COVERAGE_THRESHOLD = 0.7;

    public SkillAffinity {
        if (skillTag == null || skillTag.isBlank()) {
            throw new DomainException("The skill tag cannot be empty.");
        }
        if (Double.isNaN(score) || score < 0 || score > 1) {
            throw new DomainException("The affinity must be between 0 and 1.");
        }
        skillTag = skillTag.strip();
    }

    /** Whether the certificate covers the skill: the affinity reaches {@link #COVERAGE_THRESHOLD}. */
    public boolean covers() {
        return score >= COVERAGE_THRESHOLD;
    }
}
