package com.innovify.skillswap.recognitionincentives.domain.model.commands;

/**
 * Credits a verifier for a final demonstration they reviewed.
 *
 * @param verifierUserId  the verifier
 * @param demonstrationId the demonstration, which can pay a wallet only once
 */
public record CreditVerifierForDemonstrationCommand(int verifierUserId, int demonstrationId) {
}
