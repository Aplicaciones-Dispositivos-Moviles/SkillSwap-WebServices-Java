package com.innovify.skillswap.recognitionincentives.interfaces.rest.resources;

/**
 * Redeems a benefit with SkillCredits.
 *
 * @param item Badge (200 SkillCredits), ContributionCertificate (300), AdvancedPathUnlock (500) or
 *             SponsoredCertification (1500)
 */
public record RedeemResource(String item) {
}
