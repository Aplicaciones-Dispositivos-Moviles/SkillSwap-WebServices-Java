package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;

/**
 * The assigned verifier resolves a case. A null decision means the request did not carry a valid one, which
 * the service rejects as InvalidDecision.
 */
public record ResolveVerificationCaseCommand(int caseId, int verifierUserId, ReviewDecision decision,
                                             String rubricNotes) {
}
