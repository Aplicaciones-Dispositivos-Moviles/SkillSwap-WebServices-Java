package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/** The student of a case attaches the link to their repository or portfolio. */
public record AttachCaseEvidenceCommand(int caseId, int studentId, String evidenceUrl) {
}
