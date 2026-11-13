package com.innovify.skillswap.learningpathengine.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.Collection;
import java.util.List;

/**
 * The difference between what a student already demonstrated and what their goal requires.
 *
 * @param verifiedSkillTags relevant skills the student already demonstrated (sorted, no duplicates)
 * @param missingSkillTags  skills still to be demonstrated, prerequisites included (sorted, no duplicates)
 */
public record SkillGap(List<String> verifiedSkillTags, List<String> missingSkillTags) {

    public SkillGap {
        List<String> verified = normalize(verifiedSkillTags);
        List<String> missing = normalize(missingSkillTags);
        if (verified.stream().anyMatch(missing::contains)) {
            throw new DomainException("A skill cannot be both verified and missing.");
        }
        verifiedSkillTags = verified;
        missingSkillTags = missing;
    }

    /** Whether there is nothing left to demonstrate. */
    public boolean isEmpty() {
        return missingSkillTags.isEmpty();
    }

    private static List<String> normalize(Collection<String> tags) {
        return (tags == null ? List.<String>of() : tags).stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .sorted()
                .toList();
    }
}
