package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A student cancels a scheduled demonstration.
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student (the authenticated user)
 */
public record CancelFinalDemonstrationCommand(int demonstrationId, int studentId) {
}
