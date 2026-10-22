package com.innovify.skillswap.assessmentpeerreview.application.acl;

/**
 * How often a sub-topic was where the students failed the most in a period.
 *
 * @param skillTag        the skill the sub-topic belongs to
 * @param subTopic        the sub-topic
 * @param weakCount       the failed attempts where it was the weakest sub-topic
 * @param shareOfFailures weakCount / the failed attempts of the skill, from 0 to 1, rounded to two decimals
 */
public record SubTopicDifficultyView(String skillTag, String subTopic, int weakCount, double shareOfFailures) {
}
