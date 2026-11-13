package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import java.time.Instant;

/**
 * A student schedules the final demonstration of a completed path.
 *
 * @param studentId      the student (the authenticated user)
 * @param learningPathId the completed path
 * @param modality       advanced project or supervised exam
 * @param scheduledAt    the moment of the demonstration, at least 24 hours ahead
 */
public record ScheduleFinalDemonstrationCommand(int studentId, int learningPathId, DemonstrationModality modality,
                                                Instant scheduledAt) {
}
