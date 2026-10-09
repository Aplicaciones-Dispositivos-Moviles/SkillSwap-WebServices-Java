package com.innovify.skillswap.recognitionincentives.domain.model.commands;

/**
 * Credits a verifier for a case they resolved.
 *
 * @param verifierUserId the verifier
 * @param caseId         the case, which can pay a wallet only once
 */
public record CreditVerifierCommand(int verifierUserId, int caseId) {
}
