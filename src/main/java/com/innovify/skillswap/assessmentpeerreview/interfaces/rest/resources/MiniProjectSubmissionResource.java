package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The delivery of a mini-project and its preliminary evaluation by the AI (US19).
 *
 * @param id                     the unique identifier of the delivery
 * @param miniProjectId          the mini-project that was delivered
 * @param pathNodeId             the node it assesses
 * @param skillTag               the skill being demonstrated
 * @param repositoryUrl          the link to the repository
 * @param submittedAt            when it was delivered (UTC)
 * @param evaluationStatus       Pending (the AI is evaluating it), Approved (the AI approved it and the node is
 *                               completed), Escalated (a verifier reviews it in the verification case) or
 *                               EscalationLimitReached (it needed a verifier, but the monthly escalations of the plan
 *                               were used up)
 * @param preliminaryScore       the score the AI gave against the rubric (0 to 100); null while pending or when the AI
 *                               could not evaluate it
 * @param preliminaryConfidence  how sure the AI was of the score (0 to 100; below 70 a verifier reviews it); null while
 *                               pending or when the AI could not evaluate it
 * @param preliminaryFeedback    what the AI suggests improving; null when there is none
 * @param evaluatedAt            when the preliminary evaluation settled the delivery (UTC); null while pending
 * @param verificationCaseId     the case opened for a verifier; null unless the status is Escalated
 * @param verificationCaseStatus Pending, Assigned or Resolved; null when no case was opened
 */
public record MiniProjectSubmissionResource(
        int id, int miniProjectId, int pathNodeId, String skillTag, String repositoryUrl, Instant submittedAt,
        @Schema(allowableValues = {"Pending", "Approved", "Escalated", "EscalationLimitReached"})
        String evaluationStatus,
        Integer preliminaryScore, Integer preliminaryConfidence, String preliminaryFeedback, Instant evaluatedAt,
        Integer verificationCaseId, String verificationCaseStatus) {
}
