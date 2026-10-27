package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The final demonstration of a completed path (US28).
 *
 * @param id             the unique identifier of the demonstration
 * @param studentId      the student
 * @param learningPathId the completed path
 * @param skillTag       the skill of the path the assigned verifier is enabled in
 * @param verifierUserId the assigned verifier
 * @param modality       AdvancedProject or SupervisedExam
 * @param scheduledAt    when the demonstration takes place (UTC)
 * @param status         Scheduled, VideoSubmitted, Approved, Rejected or Cancelled
 * @param createdAt      when it was scheduled (UTC)
 * @param updatedAt      when it last changed (UTC)
 */
public record FinalDemonstrationResource(
        int id, int studentId, int learningPathId, String skillTag, int verifierUserId,
        @Schema(allowableValues = {"AdvancedProject", "SupervisedExam"}) String modality,
        Instant scheduledAt,
        @Schema(allowableValues = {"Scheduled", "VideoSubmitted", "Approved", "Rejected", "Cancelled"})
        String status,
        Instant createdAt, Instant updatedAt) {
}
