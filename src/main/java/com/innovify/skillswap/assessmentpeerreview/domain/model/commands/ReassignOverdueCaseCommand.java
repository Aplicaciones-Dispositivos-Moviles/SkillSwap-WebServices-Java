package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * The deadline of an assigned case passed without a resolution: the breach of the verifier is recorded and the case
 * goes to another verifier (US39). Repeating the command changes nothing once the case was handled.
 */
public record ReassignOverdueCaseCommand(int caseId) {
}
