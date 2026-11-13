package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

import java.time.Instant;

/**
 * A student moves a scheduled demonstration to another moment.
 *
 * @param demonstrationId the demonstration
 * @param studentId       the student (the authenticated user)
 * @param scheduledAt     the new moment, at least 24 hours ahead
 */
public record RescheduleFinalDemonstrationCommand(int demonstrationId, int studentId, Instant scheduledAt) {
}
