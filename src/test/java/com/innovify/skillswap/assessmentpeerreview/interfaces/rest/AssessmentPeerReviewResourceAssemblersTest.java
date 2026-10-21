package com.innovify.skillswap.assessmentpeerreview.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitAssessmentAttemptOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptDetail;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FailedQuestion;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.AssessmentAttemptResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.MiniProjectSubmissionResource;
import com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources.ReevaluationResource;
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
    void attempt_withoutASubTopic_hasNoneToReinforce() {
        assertThat(AssessmentPeerReviewResourceAssemblers.toResource(attempt(List.of(0, 0, 0, 0, 0)))
                .weakSubTopic()).isNull();
    }

    @Test
    void attempt_justSubmitted_carriesTheSubTopicToReinforce() {
        var outcome = new SubmitAssessmentAttemptOutcome(attempt(List.of(0, 0, 0, 0, 0)), verificationCase(), null,
                "Indexes");

        AssessmentAttemptResource resource = AssessmentPeerReviewResourceAssemblers.toResource(outcome);

        assertThat(resource.weakSubTopic()).isEqualTo("Indexes");
        assertThat(resource.verificationCaseId()).isEqualTo(9);
    }

    @Test
    void attempt_readById_carriesTheSubTopicToReinforce() {
        var detail = new AssessmentAttemptDetail(attempt(List.of(1, 2, 3, 0, 3)), "Keys");

        AssessmentAttemptResource resource = AssessmentPeerReviewResourceAssemblers.toResource(detail);

        assertThat(resource.id()).isEqualTo(3);
        assertThat(resource.passed()).isTrue();
        assertThat(resource.weakSubTopic()).isEqualTo("Keys");
        assertThat(resource.verificationCaseId()).isNull();
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

    @Test
    void reevaluation_carriesItsSourceTheSeniorAndTheJustification() {
        Reevaluation reevaluation = new Reevaluation(ReevaluationSourceType.CASE, 9, 7, 4, "Doubts about the rubric.");
        ReflectionTestUtils.setField(reevaluation, "id", 2);

        ReevaluationResource resource = AssessmentPeerReviewResourceAssemblers.toResource(reevaluation);

        assertThat(resource.id()).isEqualTo(2);
        assertThat(resource.sourceType()).isEqualTo("Case");
        assertThat(resource.sourceId()).isEqualTo(9);
        assertThat(resource.studentId()).isEqualTo(7);
        assertThat(resource.orderedByUserId()).isEqualTo(4);
        assertThat(resource.justification()).isEqualTo("Doubts about the rubric.");
        assertThat(resource.createdAt()).isNotNull();
    }

    // ---------- Mini-project deliveries ----------

    private static MiniProjectSubmission submission() {
        MiniProjectSubmission submission = new MiniProjectSubmission(4, 7, 10, "http-basics",
                "https://github.com/ana/http-basics");
        ReflectionTestUtils.setField(submission, "id", 6);
        return submission;
    }

    private static VerificationCase miniProjectCase() {
        VerificationCase verificationCase = VerificationCase.forMiniProject(6, 7, 10, "http-basics",
                "https://github.com/ana/http-basics", ReviewDeadline.businessDays(5));
        ReflectionTestUtils.setField(verificationCase, "id", 12);
        return verificationCase;
    }

    @Test
    void submission_rightAfterTheDelivery_isPendingWithoutScoresNorCase() {
        MiniProjectSubmissionResource resource = AssessmentPeerReviewResourceAssemblers.toResource(submission());

        assertThat(resource.id()).isEqualTo(6);
        assertThat(resource.miniProjectId()).isEqualTo(4);
        assertThat(resource.pathNodeId()).isEqualTo(10);
        assertThat(resource.skillTag()).isEqualTo("http-basics");
        assertThat(resource.repositoryUrl()).isEqualTo("https://github.com/ana/http-basics");
        assertThat(resource.submittedAt()).isNotNull();
        assertThat(resource.evaluationStatus()).isEqualTo("Pending");
        assertThat(resource.preliminaryScore()).isNull();
        assertThat(resource.preliminaryConfidence()).isNull();
        assertThat(resource.preliminaryFeedback()).isNull();
        assertThat(resource.evaluatedAt()).isNull();
        assertThat(resource.verificationCaseId()).isNull();
        assertThat(resource.verificationCaseStatus()).isNull();
    }

    @Test
    void submission_escalated_carriesTheEvaluationAndItsCase() {
        MiniProjectSubmission escalated = submission()
                .escalate(new PreliminaryEvaluation(55, 80, "Add tests."));

        MiniProjectSubmissionResource resource = AssessmentPeerReviewResourceAssemblers.toResource(escalated,
                miniProjectCase());

        assertThat(resource.evaluationStatus()).isEqualTo("Escalated");
        assertThat(resource.preliminaryScore()).isEqualTo(55);
        assertThat(resource.preliminaryConfidence()).isEqualTo(80);
        assertThat(resource.preliminaryFeedback()).isEqualTo("Add tests.");
        assertThat(resource.evaluatedAt()).isNotNull();
        assertThat(resource.verificationCaseId()).isEqualTo(12);
        assertThat(resource.verificationCaseStatus()).isEqualTo("Pending");
    }

    @Test
    void submission_approvedOrWithoutEscalationsLeft_hasNoCase() {
        MiniProjectSubmissionResource approved = AssessmentPeerReviewResourceAssemblers.toResource(
                submission().approve(new PreliminaryEvaluation(85, 90, ""), 70), null);
        MiniProjectSubmissionResource limited = AssessmentPeerReviewResourceAssemblers.toResource(
                submission().keepWithoutReview(null), null);

        assertThat(approved.evaluationStatus()).isEqualTo("Approved");
        assertThat(approved.preliminaryFeedback()).isNull();
        assertThat(approved.verificationCaseId()).isNull();
        assertThat(limited.evaluationStatus()).isEqualTo("EscalationLimitReached");
        assertThat(limited.preliminaryScore()).isNull();
        assertThat(limited.verificationCaseId()).isNull();
    }

    @Test
    void verificationCase_ofAMiniProject_hasASubmissionAndNoAttempt() {
        VerificationCaseResource resource = AssessmentPeerReviewResourceAssemblers.toResource(miniProjectCase());

        assertThat(resource.attemptId()).isNull();
        assertThat(resource.submissionId()).isEqualTo(6);
        assertThat(resource.caseType()).isEqualTo("MiniProject");
        assertThat(resource.evidenceUrl()).isEqualTo("https://github.com/ana/http-basics");
    }

    @Test
    void verificationCase_ofAQuiz_hasAnAttemptAndNoSubmission() {
        VerificationCaseResource resource = AssessmentPeerReviewResourceAssemblers.toResource(verificationCase());

        assertThat(resource.attemptId()).isEqualTo(3);
        assertThat(resource.submissionId()).isNull();
    }

    @Test
    void detail_ofAMiniProjectCase_hasNoAttemptAndNoFailedQuestions() {
        VerificationCaseDetailResource resource = AssessmentPeerReviewResourceAssemblers.toResource(
                new VerificationCaseDetail(miniProjectCase(), null, List.of()));

        assertThat(resource.verificationCase().id()).isEqualTo(12);
        assertThat(resource.attempt()).isNull();
        assertThat(resource.failedQuestions()).isEmpty();
    }
}
