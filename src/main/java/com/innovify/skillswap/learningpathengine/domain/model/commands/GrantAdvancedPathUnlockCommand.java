package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * Grants the advanced path a student redeemed with SkillCredits. Granting the same redemption again changes nothing.
 *
 * @param studentId    the student
 * @param redemptionId the redemption of Recognition &amp; Incentives that paid it
 */
public record GrantAdvancedPathUnlockCommand(int studentId, int redemptionId) {
}
