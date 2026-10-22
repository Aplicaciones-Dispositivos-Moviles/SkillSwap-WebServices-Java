package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform;

import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.interfaces.rest.problemdetails.ProblemDetails;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps application results and domain errors to HTTP responses. */
public final class AssessmentPeerReviewActionResultAssembler {

    private AssessmentPeerReviewActionResultAssembler() {
    }

    public static HttpStatus toStatusFromError(AssessmentPeerReviewError error) {
        return switch (error) {
            case NONE, INVALID_ANSWERS, INVALID_EVIDENCE_URL, INVALID_SKILL_TAG, RUBRIC_NOTES_REQUIRED,
                 INVALID_DECISION, INVALID_AVAILABILITY, RUBRIC_NOTES_TOO_LONG, INVALID_REVIEW_DEADLINE, JUSTIFICATION_REQUIRED, JUSTIFICATION_TOO_LONG,
                 INVALID_SOURCE_TYPE, INVALID_REPOSITORY_URL ->
                    HttpStatus.BAD_REQUEST;
            case BLUEPRINT_NOT_FOUND, ATTEMPT_NOT_FOUND, CASE_NOT_FOUND, VERIFIER_PROFILE_NOT_FOUND,
                 MINI_PROJECT_NOT_FOUND, MINI_PROJECT_SUBMISSION_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case NOT_BLUEPRINT_OWNER, NOT_ATTEMPT_OWNER, NOT_CASE_OWNER, NOT_ASSIGNED_VERIFIER, NOT_A_VERIFIER,
                 NOT_SENIOR_VERIFIER, CANNOT_REEVALUATE_OWN_WORK, NOT_MINI_PROJECT_OWNER, NOT_SUBMISSION_OWNER ->
                    HttpStatus.FORBIDDEN;
            case BLUEPRINT_OUTDATED, ATTEMPT_ALREADY_SUBMITTED, NODE_NOT_AVAILABLE, OPEN_CASE_ALREADY_EXISTS,
                 CASE_ALREADY_RESOLVED, CASE_NOT_ASSIGNED, CASE_NOT_APPEALABLE, APPEAL_ALREADY_USED,
                 SKILL_NOT_COMPLETED, VERIFIER_SKILL_ALREADY_ENABLED, ATTEMPT_NOT_PASSED, CASE_NOT_APPROVED,
                 NODE_NOT_REOPENABLE, MINI_PROJECT_OUTDATED, MINI_PROJECT_EXPIRED, MINI_PROJECT_ALREADY_SUBMITTED,
                 MINI_PROJECT_ALREADY_EVALUATED, ALREADY_SUSPENDED, NOT_SUSPENDED, CANNOT_SUSPEND_SELF,
                 NOT_LOW_RELIABILITY, OPERATION_CANCELLED -> HttpStatus.CONFLICT;
            case DATABASE_ERROR, INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static <T> ResponseEntity<?> toResponse(Result<T> result, Function<T, ResponseEntity<?>> onSuccess) {
        if (result.isSuccess()) {
            return onSuccess.apply(result.value());
        }
        AssessmentPeerReviewError error = (AssessmentPeerReviewError) result.error();
        Map<String, Object> details = result.details() == null ? Map.of() : result.details();
        return ProblemDetails.of(toStatusFromError(error), error, result.message(), details);
    }

    public static ResponseEntity<?> toError(AssessmentPeerReviewError error, String localizedMessage) {
        return ProblemDetails.of(toStatusFromError(error), error, localizedMessage);
    }
}
