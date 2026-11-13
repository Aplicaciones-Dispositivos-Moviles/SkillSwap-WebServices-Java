package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Walks the prerequisites of the goal's skills. A verified skill stops the walk: its own prerequisites are
 * assumed to be demonstrated. A prerequisite reachable through another, unverified skill is still required.
 */
public class DefaultSkillGapAnalyzer implements SkillGapAnalyzer {

    private final SkillTaxonomy taxonomy;

    public DefaultSkillGapAnalyzer(SkillTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override
    public SkillGap analyze(CareerGoal goal, Collection<String> verifiedSkillTags) {
        Set<String> verified = new HashSet<>(verifiedSkillTags);
        Set<String> missing = new HashSet<>();
        Set<String> relevantVerified = new HashSet<>();

        Deque<String> pending = new ArrayDeque<>(goal.mappedSkillTags());
        while (!pending.isEmpty()) {
            String tag = pending.pop();
            if (verified.contains(tag)) {
                relevantVerified.add(tag);
                continue;
            }
            if (!missing.add(tag)) {
                continue;
            }
            taxonomy.prerequisitesOf(tag).forEach(pending::push);
        }

        return new SkillGap(relevantVerified.stream().toList(), missing.stream().toList());
    }
}
