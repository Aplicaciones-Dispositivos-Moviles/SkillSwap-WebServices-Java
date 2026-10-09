package com.innovify.skillswap.assessmentpeerreview;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.AssessmentAttemptCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitAssessmentAttemptOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerificationCaseCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierProfileCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptQueryService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FailedQuestion;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseQueryService;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerifierProfileQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AppealVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCasesByVerifierQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.commandservices.AssessmentBlueprintCommandService;
import com.innovify.skillswap.learningpathengine.application.commandservices.LearningPathCommandService;
import com.innovify.skillswap.learningpathengine.application.queryservices.LearningPathQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathByStudentIdQuery;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The Assessment &amp; Peer Review services against the real Spring wiring, the real Learning Path Engine and a
 * real PostgreSQL. Every student declares a goal, so the node "networking-basics" is available to answer.
 */
class AssessmentPeerReviewServicesIntegrationTest extends PostgresIntegrationTest {

    private static final String GOAL = "quiero aprender a construir APIs REST con autenticación JWT";
    private static final String SKILL = "networking-basics";

    @Autowired
    private AssessmentAttemptCommandService attemptCommands;

    @Autowired
    private VerificationCaseCommandService caseCommands;

    @Autowired
    private VerifierProfileCommandService profileCommands;

    @Autowired
    private AssessmentAttemptQueryService attemptQueries;

    @Autowired
    private VerificationCaseQueryService caseQueries;

    @Autowired
    private VerifierProfileQueryService profileQueries;

    @Autowired
    private VerifierProfileContextFacade profileFacade;

    @Autowired
    private LearningPathCommandService pathCommands;

    @Autowired
    private LearningPathQueryService pathQueries;

    @Autowired
    private AssessmentBlueprintCommandService blueprintCommands;

    @Autowired
    private LearningPathContextFacade learningPath;

    // ---------- Helpers ----------

    private LearningPath pathOf(int studentId) {
        return pathQueries.handle(new GetLearningPathByStudentIdQuery(studentId)).orElseThrow();
    }

    private PathNode node(int studentId, String skill) {
        return pathOf(studentId).getNodes().stream().filter(n -> n.getSkillTag().equals(skill)).findFirst()
                .orElseThrow();
    }

    /** Declares the goal of the student (once) and generates a new assessment for the node of the skill. */
    private BlueprintView newBlueprint(int studentId) {
        if (pathQueries.handle(new GetLearningPathByStudentIdQuery(studentId)).isEmpty()) {
            assertThat(pathCommands.handle(new DeclareGoalCommand(studentId, GOAL)).isSuccess()).isTrue();
        }
        Result<AssessmentBlueprint> generated = blueprintCommands.handle(
                new GenerateAssessmentBlueprintCommand(node(studentId, SKILL).getId(), studentId));
        assertThat(generated.isSuccess()).as(generated.message()).isTrue();
        return learningPath.getBlueprint(generated.value().getId()).orElseThrow();
    }

    private static List<Integer> passing(BlueprintView blueprint) {
        return blueprint.questions().stream().map(q -> q.correctAnswer()).toList();
    }

    private static List<Integer> failing(BlueprintView blueprint) {
        return blueprint.questions().stream().map(q -> (q.correctAnswer() + 1) % 4).toList();
    }

    private Result<SubmitAssessmentAttemptOutcome> submit(int studentId, BlueprintView blueprint,
                                                          List<Integer> answers) {
        return attemptCommands.handle(new SubmitAssessmentAttemptCommand(studentId, blueprint.blueprintId(), answers));
    }

    private SubmitAssessmentAttemptOutcome passAssessment(int studentId) {
        BlueprintView blueprint = newBlueprint(studentId);
        Result<SubmitAssessmentAttemptOutcome> result = submit(studentId, blueprint, passing(blueprint));
        assertThat(result.isSuccess()).as(result.message()).isTrue();
        return result.value();
    }

    private SubmitAssessmentAttemptOutcome failAssessment(int studentId) {
        BlueprintView blueprint = newBlueprint(studentId);
        Result<SubmitAssessmentAttemptOutcome> result = submit(studentId, blueprint, failing(blueprint));
        assertThat(result.isSuccess()).as(result.message()).isTrue();
        return result.value();
    }

    /** The student passes the assessment of the skill and becomes a verifier of it. */
    private void enrollVerifier(int userId) {
        passAssessment(userId);
        Result<VerifierProfile> result = profileCommands.handle(new CreateVerifierProfileCommand(userId, SKILL));
        assertThat(result.isSuccess()).as(result.message()).isTrue();
    }

    private VerificationCase reload(int caseId) {
        return caseQueries.handle(new GetVerificationCaseByIdQuery(caseId)).orElseThrow();
    }

    // ---------- Wiring ----------

    @Test
    void theServicesAreWired() {
        assertThat(attemptCommands).isNotNull();
        assertThat(caseCommands).isNotNull();
        assertThat(profileCommands).isNotNull();
        assertThat(profileFacade).isNotNull();
    }

    // ---------- Attempts ----------

    @Test
    void submit_passingAnswers_persistTheAttemptAndCompleteTheNode() {
        SubmitAssessmentAttemptOutcome outcome = passAssessment(1);

        assertThat(outcome.verificationCase()).isNull();
        assertThat(outcome.attempt().getId()).isPositive();
        assertThat(attemptQueries.handle(new GetAssessmentAttemptByIdQuery(outcome.attempt().getId()))).isPresent();
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(node(1, "http-basics").getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void submit_failingAnswersWithoutVerifiers_leaveAPendingCase() {
        SubmitAssessmentAttemptOutcome outcome = failAssessment(1);

        assertThat(outcome.attempt().isPassed()).isFalse();
        VerificationCase stored = reload(outcome.verificationCase().getId());
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(stored.getAttemptId()).isEqualTo(outcome.attempt().getId());
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void submit_whileACaseIsOpen_isRejected() {
        failAssessment(1);
        BlueprintView again = newBlueprint(1);

        Result<SubmitAssessmentAttemptOutcome> result = submit(1, again, passing(again));

        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
    }

    @Test
    void submit_twiceForTheSameBlueprint_isRejected() {
        BlueprintView blueprint = newBlueprint(1);
        submit(1, blueprint, passing(blueprint));

        Result<SubmitAssessmentAttemptOutcome> result = submit(1, blueprint, passing(blueprint));

        // The node is completed by the first attempt, so it is no longer available.
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
    }

    @Test
    void submit_forAnotherStudentsAssessment_isRejected() {
        BlueprintView blueprint = newBlueprint(1);

        Result<SubmitAssessmentAttemptOutcome> result = submit(2, blueprint, passing(blueprint));

        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NOT_BLUEPRINT_OWNER);
    }

    // ---------- Verifiers and assignment ----------

    @Test
    void verifierProfile_requiresTheCompletedSkill() {
        Result<VerifierProfile> result = profileCommands.handle(new CreateVerifierProfileCommand(2, SKILL));

        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.SKILL_NOT_COMPLETED);
    }

    @Test
    void verifierProfile_isPersistedAndEnabledForTheSkill() {
        enrollVerifier(2);

        VerifierProfile profile = profileQueries.handle(new GetVerifierProfileByUserIdQuery(2)).orElseThrow();
        assertThat(profile.getSkillTags()).containsExactly(SKILL);
        assertThat(profile.isAvailable()).isTrue();
        assertThat(profile.isVerified()).isTrue();
    }

    @Test
    void submit_failingAnswersWithAVerifier_assignTheCaseRightAway() {
        enrollVerifier(2);

        SubmitAssessmentAttemptOutcome outcome = failAssessment(1);

        VerificationCase stored = reload(outcome.verificationCase().getId());
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(stored.getVerifierUserId()).isEqualTo(2);
        assertThat(caseQueries.handle(new GetVerificationCasesByVerifierQuery(2)))
                .extracting(VerificationCase::getId).containsExactly(stored.getId());
    }

    @Test
    void enrollingAVerifier_assignsThePendingCasesOfTheSkill() {
        SubmitAssessmentAttemptOutcome pending = failAssessment(1);
        assertThat(reload(pending.verificationCase().getId()).getStatus()).isEqualTo(CaseStatus.PENDING);

        enrollVerifier(2);

        VerificationCase stored = reload(pending.verificationCase().getId());
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(stored.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void switchingAvailabilityOff_leavesNewCasesPendingUntilItIsSwitchedOn() {
        enrollVerifier(2);
        profileCommands.handle(new UpdateVerifierAvailabilityCommand(2, false));

        SubmitAssessmentAttemptOutcome outcome = failAssessment(1);
        assertThat(reload(outcome.verificationCase().getId()).getStatus()).isEqualTo(CaseStatus.PENDING);

        Result<VerifierProfile> result = profileCommands.handle(new UpdateVerifierAvailabilityCommand(2, true));

        assertThat(result.value().isAvailable()).isTrue();
        assertThat(reload(outcome.verificationCase().getId()).getStatus()).isEqualTo(CaseStatus.ASSIGNED);
    }

    // ---------- Reviewing ----------

    @Test
    void resolve_asApproved_completesTheNodeAndCountsTheReview() {
        enrollVerifier(2);
        int caseId = failAssessment(1).verificationCase().getId();

        Result<VerificationCase> result = caseCommands.handle(
                new ResolveVerificationCaseCommand(caseId, 2, ReviewDecision.APPROVED, "Meets the rubric."));

        assertThat(result.isSuccess()).as(result.message()).isTrue();
        VerificationCase stored = reload(caseId);
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(stored.getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(stored.getRubricNotes()).isEqualTo("Meets the rubric.");
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(profileQueries.handle(new GetVerifierProfileByUserIdQuery(2)).orElseThrow().getReviewCount())
                .isEqualTo(1);
    }

    @Test
    void resolve_asRejected_keepsTheNodeAndLetsTheStudentTryAgain() {
        enrollVerifier(2);
        int caseId = failAssessment(1).verificationCase().getId();

        Result<VerificationCase> result = caseCommands.handle(
                new ResolveVerificationCaseCommand(caseId, 2, ReviewDecision.REJECTED, "Needs more practice."));

        assertThat(result.isSuccess()).as(result.message()).isTrue();
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.AVAILABLE);

        SubmitAssessmentAttemptOutcome retry = passAssessment(1);
        assertThat(retry.attempt().isPassed()).isTrue();
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.COMPLETED);
    }

    @Test
    void resolve_byAVerifierWhoIsNotAssigned_isRejected() {
        enrollVerifier(2);
        enrollVerifier(3);
        VerificationCase assigned = reload(failAssessment(1).verificationCase().getId());
        int other = assigned.getVerifierUserId() == 2 ? 3 : 2;

        Result<VerificationCase> result = caseCommands.handle(
                new ResolveVerificationCaseCommand(assigned.getId(), other, ReviewDecision.APPROVED, "Fine."));

        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NOT_ASSIGNED_VERIFIER);
        assertThat(reload(assigned.getId()).getStatus()).isEqualTo(CaseStatus.ASSIGNED);
    }

    @Test
    void detail_listsTheFailedQuestionsWithoutTheCorrectAnswers() {
        enrollVerifier(2);
        SubmitAssessmentAttemptOutcome outcome = failAssessment(1);

        VerificationCaseDetail detail = caseQueries
                .handle(new GetVerificationCaseDetailQuery(outcome.verificationCase().getId())).orElseThrow();

        assertThat(detail.failedQuestions()).hasSize(5);
        assertThat(detail.failedQuestions()).extracting(FailedQuestion::position).containsExactly(1, 2, 3, 4, 5);
        assertThat(detail.attempt().getId()).isEqualTo(outcome.attempt().getId());
    }

    @Test
    void evidence_isAttachedToTheCaseOfTheStudent() {
        int caseId = failAssessment(1).verificationCase().getId();

        Result<VerificationCase> result = caseCommands.handle(
                new AttachCaseEvidenceCommand(caseId, 1, "https://github.com/student/project"));

        assertThat(result.isSuccess()).as(result.message()).isTrue();
        assertThat(reload(caseId).getEvidenceUrl()).isEqualTo("https://github.com/student/project");
    }

    // ---------- Facade ----------

    @Test
    void updateRating_isPersistedWithoutTouchingTheReviewCount() {
        enrollVerifier(2);

        assertThat(profileFacade.updateRating(2, 4.5)).isTrue();
        assertThat(profileFacade.updateRating(99, 4.5)).isFalse();

        VerifierProfile profile = profileQueries.handle(new GetVerifierProfileByUserIdQuery(2)).orElseThrow();
        assertThat(profile.getRating()).isEqualTo(4.5);
        assertThat(profile.getReviewCount()).isZero();
    }

    // ---------- Appeals ----------

    private Result<VerificationCase> appeal(int caseId, int studentId) {
        return caseCommands.handle(new AppealVerificationCaseCommand(caseId, studentId));
    }

    private void reject(int caseId, int verifierId) {
        Result<VerificationCase> result = caseCommands.handle(
                new ResolveVerificationCaseCommand(caseId, verifierId, ReviewDecision.REJECTED, "Needs more practice."));
        assertThat(result.isSuccess()).as(result.message()).isTrue();
    }

    @Test
    void appeal_goesToAnotherVerifierWhoseApprovalCompletesTheNode() {
        enrollVerifier(2);
        enrollVerifier(3);
        int caseId = failAssessment(1).verificationCase().getId();
        assertThat(reload(caseId).getVerifierUserId()).isEqualTo(2);
        reject(caseId, 2);

        Result<VerificationCase> appealed = appeal(caseId, 1);

        assertThat(appealed.isSuccess()).as(appealed.message()).isTrue();
        VerificationCase stored = reload(caseId);
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(stored.getVerifierUserId()).isEqualTo(3);
        assertThat(stored.getPreviousVerifierUserId()).isEqualTo(2);
        assertThat(stored.getAppealCount()).isEqualTo(1);

        Result<VerificationCase> resolved = caseCommands.handle(
                new ResolveVerificationCaseCommand(caseId, 3, ReviewDecision.APPROVED, "It does meet the rubric."));

        assertThat(resolved.isSuccess()).as(resolved.message()).isTrue();
        assertThat(node(1, SKILL).getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(appeal(caseId, 1).isFailure()).isTrue();
    }

    @Test
    void appeal_withoutAnotherVerifier_waitsUntilOneIsEnrolled() {
        enrollVerifier(2);
        int caseId = failAssessment(1).verificationCase().getId();
        reject(caseId, 2);

        Result<VerificationCase> appealed = appeal(caseId, 1);

        assertThat(appealed.isSuccess()).as(appealed.message()).isTrue();
        assertThat(reload(caseId).getStatus()).isEqualTo(CaseStatus.PENDING);

        enrollVerifier(3);

        VerificationCase stored = reload(caseId);
        assertThat(stored.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(stored.getVerifierUserId()).isEqualTo(3);
    }

    @Test
    void appeal_ofAnotherStudentsCase_isRejected() {
        enrollVerifier(2);
        int caseId = failAssessment(1).verificationCase().getId();
        reject(caseId, 2);

        Result<VerificationCase> result = appeal(caseId, 5);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NOT_CASE_OWNER);
        assertThat(reload(caseId).getStatus()).isEqualTo(CaseStatus.RESOLVED);
    }

    @Test
    void appeal_afterTheStudentTriedAgainAndFailed_isRejectedBecauseTheNodeHasAnOpenCase() {
        enrollVerifier(2);
        int firstCase = failAssessment(1).verificationCase().getId();
        reject(firstCase, 2);
        failAssessment(1);

        Result<VerificationCase> result = appeal(firstCase, 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
    }
}
