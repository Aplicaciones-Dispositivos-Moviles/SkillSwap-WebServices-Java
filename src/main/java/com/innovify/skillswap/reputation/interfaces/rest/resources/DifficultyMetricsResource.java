package com.innovify.skillswap.reputation.interfaces.rest.resources;

import java.time.LocalDate;
import java.util.List;

/** Where the students fail the most in the period (from and to inclusive). Rates go from 0 to 1. */
public record DifficultyMetricsResource(LocalDate from, LocalDate to, int minAttempts,
                                        List<SkillDifficultyResource> skills,
                                        List<SubTopicDifficultyResource> subTopics) {

    /** A skill with its attempts, failed attempts and failure rate. */
    public record SkillDifficultyResource(String skillTag, String skillName, int attempts, int failed,
                                          double failureRate) {
    }

    /** A sub-topic that was the weakest of failed attempts, and its share of the skill's failures. */
    public record SubTopicDifficultyResource(String skillTag, String skillName, String subTopic, int weakCount,
                                             double shareOfFailures) {
    }
}
