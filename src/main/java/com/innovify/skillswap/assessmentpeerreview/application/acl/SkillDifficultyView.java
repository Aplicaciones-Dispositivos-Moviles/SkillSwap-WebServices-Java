package com.innovify.skillswap.assessmentpeerreview.application.acl;

/**
 * How hard the assessment of a skill was in a period.
 *
 * @param skillTag    the skill
 * @param attempts    the attempts made at its assessments
 * @param failed      the attempts that did not pass
 * @param failureRate failed / attempts, from 0 to 1, rounded to two decimals
 */
public record SkillDifficultyView(String skillTag, int attempts, int failed, double failureRate) {
}
