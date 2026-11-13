package com.innovify.skillswap.recognitionincentives.domain.model.commands;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.ResolvedCaseType;

/**
 * Credits a verifier for a case they resolved.
 *
 * @param verifierUserId the verifier
 * @param caseId         the case, which can pay a wallet only once
 * @param caseType       the work reviewed, which sets the amount
 */
public record CreditVerifierCommand(int verifierUserId, int caseId, ResolvedCaseType caseType) {
}
