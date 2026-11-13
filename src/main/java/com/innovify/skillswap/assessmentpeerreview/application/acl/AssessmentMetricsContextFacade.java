package com.innovify.skillswap.assessmentpeerreview.application.acl;

import java.time.Instant;
import java.util.List;

/**
 * Anti-corruption facade through which other bounded contexts (Reputation, for the platform metrics) read aggregated
 * data of Assessment &amp; Peer Review, without depending on its aggregates or repositories.
 */
public interface AssessmentMetricsContextFacade {

    /**
     * The skills whose assessments students failed the most, among the attempts completed from {@code from}
     * (inclusive) until {@code to} (exclusive). Only skills with at least {@code minAttempts} attempts and one failure
     * count. The highest failure rate first, then the most attempts, then the tag. Empty for an empty period or a
     * limit under 1.
     */
    List<SkillDifficultyView> getSkillDifficulty(Instant from, Instant to, int minAttempts, int limit);

    /**
     * The sub-topics where students failed the most: for each failed attempt of the period with a weakest
     * sub-topic, that sub-topic counts once. The most frequent first, then the highest share, skill and sub-topic.
     * Empty for an empty period or a limit under 1.
     */
    List<SubTopicDifficultyView> getSubTopicDifficulty(Instant from, Instant to, int limit);

    /** Every verifier profile (suspended ones included) with the average resolution time of their cases, by user id. */
    List<VerifierActivityView> getVerifierActivity();
}
