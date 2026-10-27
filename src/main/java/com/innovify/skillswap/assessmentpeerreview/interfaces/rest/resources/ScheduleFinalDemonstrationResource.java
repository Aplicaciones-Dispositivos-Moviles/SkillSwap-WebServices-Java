package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The student schedules the final demonstration of a completed path.
 *
 * @param learningPathId the completed path
 * @param modality       AdvancedProject (an advanced project) or SupervisedExam (an exam supervised by the verifier)
 * @param scheduledAt    the date and time of the demonstration (ISO-8601 in UTC, at least 24 hours ahead)
 */
public record ScheduleFinalDemonstrationResource(
        Integer learningPathId,
        @Schema(allowableValues = {"AdvancedProject", "SupervisedExam"}) String modality,
        Instant scheduledAt) {
}
