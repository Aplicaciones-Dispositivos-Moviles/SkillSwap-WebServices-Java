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
 * @param hasVideo       whether the student uploaded the video (read it with GET /{id}/video)
 * @param videoContentType the MIME type of the video; null without video
 * @param videoSubmittedAt when the video was uploaded (UTC); null without video
 * @param score          the score the verifier gave with the rubric (0 to 100; 70 or more approves); null until the
 *                       review
 * @param feedback       what the verifier suggests improving; null when there is none
 * @param reviewedAt     when the verifier reviewed it (UTC); null until the review
 */
public record FinalDemonstrationResource(
        int id, int studentId, int learningPathId, String skillTag, int verifierUserId,
        @Schema(allowableValues = {"AdvancedProject", "SupervisedExam"}) String modality,
        Instant scheduledAt,
        @Schema(allowableValues = {"Scheduled", "VideoSubmitted", "Approved", "Rejected", "Cancelled"})
        String status,
        Instant createdAt, Instant updatedAt,
        boolean hasVideo, String videoContentType, Instant videoSubmittedAt, Integer score, String feedback,
        Instant reviewedAt) {
}
