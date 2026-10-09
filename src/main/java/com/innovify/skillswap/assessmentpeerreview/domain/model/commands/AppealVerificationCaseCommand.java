package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/** The student of a rejected case appeals it so a different verifier reviews it. */
public record AppealVerificationCaseCommand(int caseId, int studentId) {
}
