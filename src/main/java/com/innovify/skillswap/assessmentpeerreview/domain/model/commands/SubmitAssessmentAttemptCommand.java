package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

import java.util.List;

/** A student submits the answers of an assessment (one option index per question). */
public record SubmitAssessmentAttemptCommand(int studentId, int blueprintId, List<Integer> selectedAnswers) {
}
