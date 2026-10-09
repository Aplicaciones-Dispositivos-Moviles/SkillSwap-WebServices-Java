package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewActionResultAssembler;
import com.innovify.skillswap.shared.application.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class AssessmentPeerReviewActionResultAssemblerTest {

    @ParameterizedTest
    @CsvSource({
            "NONE,400", "INVALID_ANSWERS,400", "INVALID_EVIDENCE_URL,400", "INVALID_SKILL_TAG,400",
            "RUBRIC_NOTES_REQUIRED,400", "INVALID_DECISION,400", "INVALID_AVAILABILITY,400",
            "RUBRIC_NOTES_TOO_LONG,400",
            "BLUEPRINT_NOT_FOUND,404", "ATTEMPT_NOT_FOUND,404", "CASE_NOT_FOUND,404",
            "VERIFIER_PROFILE_NOT_FOUND,404",
            "NOT_BLUEPRINT_OWNER,403", "NOT_ATTEMPT_OWNER,403", "NOT_CASE_OWNER,403",
            "NOT_ASSIGNED_VERIFIER,403", "NOT_A_VERIFIER,403",
            "BLUEPRINT_OUTDATED,409", "ATTEMPT_ALREADY_SUBMITTED,409", "NODE_NOT_AVAILABLE,409",
            "OPEN_CASE_ALREADY_EXISTS,409", "CASE_ALREADY_RESOLVED,409", "CASE_NOT_ASSIGNED,409",
            "CASE_NOT_APPEALABLE,409", "APPEAL_ALREADY_USED,409", "SKILL_NOT_COMPLETED,409",
            "VERIFIER_SKILL_ALREADY_ENABLED,409", "OPERATION_CANCELLED,409",
            "DATABASE_ERROR,500", "INTERNAL_SERVER_ERROR,500"
    })
    void everyErrorMapsToItsStatus(AssessmentPeerReviewError error, int status) {
        assertThat(AssessmentPeerReviewActionResultAssembler.toStatusFromError(error).value()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(AssessmentPeerReviewError.class)
    void everyErrorHasAStatus(AssessmentPeerReviewError error) {
        assertThat(AssessmentPeerReviewActionResultAssembler.toStatusFromError(error)).isNotNull();
    }

    @Test
    void success_runsTheSuccessAction() {
        ResponseEntity<?> response = AssessmentPeerReviewActionResultAssembler.toResponse(Result.success("ok"),
                value -> ResponseEntity.status(HttpStatus.CREATED).body(value));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test
    void failure_becomesAProblemDetailWithTheCodeAsTitle() {
        Result<String> failure = Result.failure(AssessmentPeerReviewError.CASE_NOT_APPEALABLE, "Not appealable.");

        ResponseEntity<?> response = AssessmentPeerReviewActionResultAssembler.toResponse(failure, value -> null);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("CaseNotAppealable");
        assertThat(problem.getDetail()).isEqualTo("Not appealable.");
    }

    @Test
    void toError_buildsTheResponseFromTheLocalizedMessage() {
        ResponseEntity<?> response = AssessmentPeerReviewActionResultAssembler
                .toError(AssessmentPeerReviewError.NOT_CASE_OWNER, "Not yours.");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(((ProblemDetail) response.getBody()).getTitle()).isEqualTo("NotCaseOwner");
    }
}
