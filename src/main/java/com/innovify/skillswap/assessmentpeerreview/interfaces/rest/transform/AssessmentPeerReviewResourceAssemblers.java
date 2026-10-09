package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.AssessmentAttemptResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.FailedQuestionResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseDetailResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerifierProfileResource;

/** Builds the resources of the Assessment and Peer Review API from the aggregates. */
public final class AssessmentPeerReviewResourceAssemblers {

    private AssessmentPeerReviewResourceAssemblers() {
    }

    /** The attempt alone; the case is only known right after submitting (see the other overload). */
    public static AssessmentAttemptResource toResource(AssessmentAttempt attempt) {
        return toResource(attempt, null);
    }

    /** The attempt with the case it opened, if any. */
    public static AssessmentAttemptResource toResource(AssessmentAttempt attempt, VerificationCase verificationCase) {
        return new AssessmentAttemptResource(
                attempt.getId(),
                attempt.getBlueprintId(),
                attempt.getStudentId(),
                attempt.getScore().value(),
                attempt.getScore().total(),
                attempt.isPassed(),
                attempt.getCompletedAt(),
                verificationCase == null ? null : verificationCase.getId(),
                verificationCase == null ? null : verificationCase.getStatus().value());
    }

    public static VerificationCaseResource toResource(VerificationCase verificationCase) {
        return new VerificationCaseResource(
                verificationCase.getId(),
                verificationCase.getAttemptId(),
                verificationCase.getStudentId(),
                verificationCase.getVerifierUserId(),
                verificationCase.getPathNodeId(),
                verificationCase.getSkillTag(),
                verificationCase.getCaseType().value(),
                verificationCase.getStatus().value(),
                verificationCase.getDecision() == null ? null : verificationCase.getDecision().value(),
                verificationCase.getRubricNotes(),
                verificationCase.getEvidenceUrl(),
                verificationCase.getAppealCount(),
                verificationCase.getOpenedAt(),
                verificationCase.getAssignedAt(),
                verificationCase.getResolvedAt());
    }

    public static VerificationCaseDetailResource toResource(VerificationCaseDetail detail) {
        return new VerificationCaseDetailResource(
                toResource(detail.verificationCase()),
                toResource(detail.attempt(), detail.verificationCase()),
                detail.failedQuestions().stream()
                        .map(question -> new FailedQuestionResource(question.position(), question.text(),
                                question.answers(), question.selectedAnswer()))
                        .toList());
    }

    public static VerifierProfileResource toResource(VerifierProfile profile) {
        return new VerifierProfileResource(
                profile.getId(),
                profile.getVerifierUserId(),
                profile.getSkillTags(),
                profile.isAvailable(),
                profile.isVerified(),
                profile.getRating(),
                profile.getReviewCount(),
                profile.getCreatedAt());
    }
}
