package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

/**
 * Redeems a benefit with SkillCredits.
 *
 * @param item AdvancedPathUnlock (200 SkillCredits) or ContributionCertificate (120 SkillCredits)
 */
public record RedeemResource(String item) {
}
