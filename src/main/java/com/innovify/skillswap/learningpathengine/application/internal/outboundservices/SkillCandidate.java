package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

/**
 * A skill a text may refer to, with how well it fits.
 *
 * @param tag      the tag of the skill, as in the skill catalog
 * @param affinity how well the skill fits the text, from 0 (not at all) to 1 (exactly what the text asks)
 */
public record SkillCandidate(String tag, double affinity) {

    public SkillCandidate {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("The tag of a skill candidate is required.");
        }
        if (Double.isNaN(affinity) || affinity < 0 || affinity > 1) {
            throw new IllegalArgumentException("The affinity of a skill candidate must be between 0 and 1.");
        }
    }
}
