package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

/**
 * Redeems a benefit with SkillCredits.
 *
 * @param item AdvancedPathUnlock (50 SkillCredits) or ContributionCertificate (30 SkillCredits)
 */
public record RedeemResource(String item) {
}
