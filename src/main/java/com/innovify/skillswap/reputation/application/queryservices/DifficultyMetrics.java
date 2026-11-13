package com.innovify.skillswap.reputation.application.queryservices;

import java.time.LocalDate;
import java.util.List;

/**
 * Where the students fail the most in a period.
 *
 * @param from        first day of the period (inclusive)
 * @param to          last day of the period (inclusive)
 * @param minAttempts the attempts a skill needs in the period to be listed, so one failed attempt is not a 100% rate
 * @param skills      the skills with the highest failure rate
 * @param subTopics   the sub-topics that were most often the weakest of a failed attempt
 */
public record DifficultyMetrics(LocalDate from, LocalDate to, int minAttempts, List<SkillDifficulty> skills,
                                List<SubTopicDifficulty> subTopics) {

    /** The attempts at a skill, how many failed, and the failure rate from 0 to 1. */
    public record SkillDifficulty(String skillTag, String skillName, int attempts, int failed, double failureRate) {
    }

    /** How many failed attempts of the skill had the sub-topic as their weakest, and their share of its failures. */
    public record SubTopicDifficulty(String skillTag, String skillName, String subTopic, int weakCount,
                                     double shareOfFailures) {
    }
}
