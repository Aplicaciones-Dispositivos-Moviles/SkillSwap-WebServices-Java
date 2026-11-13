package com.innovify.skillswap.reputation.domain.model.commands;

/**
 * A verifier resolved a case.
 *
 * @param verifierUserId the verifier who resolved the case
 * @param studentId      the student whose case it was
 * @param approved       whether the verifier approved it, which certifies the student's skill
 */
public record RecordCaseResolutionCommand(int verifierUserId, int studentId, boolean approved) {
}
