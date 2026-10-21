package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * Runs the preliminary evaluation of the AI on a delivered mini-project and settles the delivery with its result
 * (US19 escenarios 2 y 3).
 *
 * @param submissionId the delivery to evaluate
 */
public record EvaluateMiniProjectSubmissionCommand(int submissionId) {
}
