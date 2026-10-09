package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FailedQuestion;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.AssessmentAttemptResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseDetailResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerificationCaseResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.VerifierProfileResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.transform.AssessmentPeerReviewResourceAssemblers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssessmentPeerReviewResourceAssemblersTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);

    private static AssessmentAttempt attempt(List<Integer> answers) {
        AssessmentAttempt attempt = new AssessmentAttempt(5, 7, answers, CORRECT);
        ReflectionTestUtils.setField(attempt, "id", 3);
        return attempt;
    }

    private static VerificationCase verificationCase() {
        VerificationCase verificationCase = new VerificationCase(3, 7, 10, "http-basics", CaseType.QUIZ);
        ReflectionTestUtils.setField(verificationCase, "id", 9);
        return verificationCase;
    }

    @Test
    void attempt_withoutACase_hasNoCaseData() {
        AssessmentAttemptResource resource = AssessmentPeerReviewResourceAssemblers.toResource(
                attempt(List.of(1, 2, 3, 0, 1)));

        assertThat(resource.id()).isEqualTo(3);
        assertThat(resource.blueprintId()).isEqualTo(5);
        assertThat(resource.studentId()).isEqualTo(7);
        assertThat(resource.score()).isEqualTo(5);
        assertThat(resource.totalQuestions()).isEqualTo(5);
        assertThat(resource.passed()).isTrue();
        assertThat(resource.completedAt()).isNotNull();
        assertThat(resource.verificationCaseId()).isNull();
        assertThat(resource.verificationCaseStatus()).isNull();
    }

    @Test
    void attempt_withACase_carriesItsIdAndStatus() {
        AssessmentAttemptResource resource = AssessmentPeerReviewResourceAssemblers.toResource(
                attempt(List.of(0, 0, 0, 0, 0)), verificationCase());

        assertThat(resource.passed()).isFalse();
        assertThat(resource.verificationCaseId()).isEqualTo(9);
        assertThat(resource.verificationCaseStatus()).isEqualTo("Pending");
    }

    @Test
    void verificationCase_exposesTheStatusAndTheDecisionAsText() {
        VerificationCase verificationCase = verificationCase().assignVerifier(2)
                .resolve(ReviewDecision.REJECTED, "Needs work.");

        VerificationCaseResource resource = AssessmentPeerReviewResourceAssemblers.toResource(verificationCase);

        assertThat(resource.id()).isEqualTo(9);
        assertThat(resource.verifierUserId()).isEqualTo(2);
        assertThat(resource.caseType()).isEqualTo("Quiz");
        assertThat(resource.status()).isEqualTo("Resolved");
        assertThat(resource.decision()).isEqualTo("Rejected");
        assertThat(resource.rubricNotes()).isEqualTo("Needs work.");
        assertThat(resource.appealCount()).isZero();
        assertThat(resource.resolvedAt()).isNotNull();
    }

    @Test
    void verificationCase_pending_hasNoDecision() {
        VerificationCaseResource resource = AssessmentPeerReviewResourceAssemblers.toResource(verificationCase());

        assertThat(resource.status()).isEqualTo("Pending");
        assertThat(resource.decision()).isNull();
        assertThat(resource.verifierUserId()).isNull();
    }

    @Test
    void detail_listsTheFailedQuestionsInOrder() {
        VerificationCaseDetail detail = new VerificationCaseDetail(verificationCase(), attempt(List.of(0, 2, 0, 0, 1)),
                List.of(new FailedQuestion(1, "Q1", List.of("a", "b", "c", "d"), 0),
                        new FailedQuestion(3, "Q3", List.of("a", "b", "c", "d"), 0)));

        VerificationCaseDetailResource resource = AssessmentPeerReviewResourceAssemblers.toResource(detail);

        assertThat(resource.verificationCase().id()).isEqualTo(9);
        assertThat(resource.attempt().id()).isEqualTo(3);
        assertThat(resource.failedQuestions()).extracting("position").containsExactly(1, 3);
        assertThat(resource.failedQuestions().get(0).question()).isEqualTo("Q1");
        assertThat(resource.failedQuestions().get(0).selectedAnswer()).isZero();
    }

    @Test
    void verifierProfile_exposesTheSkillsAndTheCounters() {
        VerifierProfile profile = new VerifierProfile(2, "http-basics");
        ReflectionTestUtils.setField(profile, "id", 4);
        profile.incrementReviewCount();

        VerifierProfileResource resource = AssessmentPeerReviewResourceAssemblers.toResource(profile);

        assertThat(resource.id()).isEqualTo(4);
        assertThat(resource.verifierUserId()).isEqualTo(2);
        assertThat(resource.skillTags()).containsExactly("http-basics");
        assertThat(resource.available()).isTrue();
        assertThat(resource.verified()).isTrue();
        assertThat(resource.reviewCount()).isEqualTo(1);
        assertThat(resource.createdAt()).isNotNull();
    }
}
