package com.innovify.skillswap.reputation.domain.model.commands;

/**
 * A student passed an assessment without a verifier.
 *
 * @param studentId the student
 */
public record RecordAutomaticApprovalCommand(int studentId) {
}
