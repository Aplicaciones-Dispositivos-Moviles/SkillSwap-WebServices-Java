package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeReviewDeadlinePolicyRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.OrderReevaluationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.learningpathengine.application.acl.NodeReopenOutcome;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

class ReevaluationCommandServiceImplTest {

    private static final int STUDENT_ID = 1;
    private static final int APPROVER_ID = 2;
    private static final int OTHER_VERIFIER_ID = 3;
    private static final int SENIOR_ID = 7;
    private static final int NODE_ID = 10;
    private static final String JUSTIFICATION = "The answers look copied.";

    private final FakeAssessmentAttemptRepository attempts = new FakeAssessmentAttemptRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeReevaluationRepository reevaluations = new FakeReevaluationRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeReviewDeadlinePolicyRepository policies = new FakeReviewDeadlinePolicyRepository();
    private final FakeSubscriptionContextFacade plans = new FakeSubscriptionContextFacade();
    private final Set<Integer> seniors = new HashSet<>();
    private ReevaluationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        ReputationContextFacade reputation = new ReputationContextFacade() {
            @Override
            public boolean isSeniorVerifier(int userId) {
                return seniors.contains(userId);
            }

            @Override
            public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
                return userIds.stream().filter(seniors::contains).collect(Collectors.toSet());
            }
        };
        service = new ReevaluationCommandServiceImpl(attempts, cases, profiles, reevaluations, reputation,
                learningPath, new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()),
                new ReviewDeadlineResolver(policies, plans), TransactionOperations.withoutTransaction(),
                TestMessages.source());
        // The senior is enabled for another skill, so they are never a candidate for the cases of these tests.
        profiles.save(new VerifierProfile(SENIOR_ID, "other-skill"));
        seniors.add(SENIOR_ID);
        learningPath.addBlueprint();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private AssessmentAttempt addAttempt(List<Integer> selected) {
        return attempts.save(new AssessmentAttempt(1, STUDENT_ID, selected, FakeLearningPathContextFacade.CORRECT));
    }

    private AssessmentAttempt addPassedAttempt() {
        return addAttempt(FakeLearningPathContextFacade.CORRECT);
    }

    private VerificationCase addApprovedCase() {
        return cases.save(new VerificationCase(1, STUDENT_ID, NODE_ID, "http-basics", CaseType.QUIZ)
                .assignVerifier(APPROVER_ID).resolve(ReviewDecision.APPROVED, "Meets the rubric."));
    }

    private Result<Reevaluation> order(ReevaluationSourceType type, int sourceId) {
        return service.handle(new OrderReevaluationCommand(SENIOR_ID, type, sourceId, JUSTIFICATION));
    }

    private static void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank();
    }

    private void assertNothingChanged() {
        assertThat(reevaluations.reevaluations()).isEmpty();
        assertThat(learningPath.reopenedNodes()).isEmpty();
    }

    // ---------- Attempt ----------

    @Test
    void order_ofAPassedAttempt_reopensTheNodeAndRecordsTheJustification() {
        AssessmentAttempt attempt = addPassedAttempt();

        Result<Reevaluation> result = order(ReevaluationSourceType.ATTEMPT, attempt.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(learningPath.reopenedNodes()).containsExactly(NODE_ID);
        Reevaluation reevaluation = result.value();
        assertThat(reevaluation.getId()).isNotNull();
        assertThat(reevaluation.getSourceType()).isEqualTo(ReevaluationSourceType.ATTEMPT);
        assertThat(reevaluation.getSourceId()).isEqualTo(attempt.getId());
        assertThat(reevaluation.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(reevaluation.getOrderedByUserId()).isEqualTo(SENIOR_ID);
        assertThat(reevaluation.getJustification()).isEqualTo(JUSTIFICATION);
        assertThat(reevaluations.reevaluations()).containsExactly(reevaluation);
    }

    @Test
    void order_ofAnUnknownAttempt_failsWithAttemptNotFound() {
        assertFailure(order(ReevaluationSourceType.ATTEMPT, 99), AssessmentPeerReviewError.ATTEMPT_NOT_FOUND);
        assertNothingChanged();
    }

    @Test
    void order_ofAnAttemptWhoseBlueprintIsGone_failsWithBlueprintNotFound() {
        attempts.save(new AssessmentAttempt(55, STUDENT_ID, FakeLearningPathContextFacade.CORRECT,
                FakeLearningPathContextFacade.CORRECT));

        assertFailure(order(ReevaluationSourceType.ATTEMPT, 1), AssessmentPeerReviewError.BLUEPRINT_NOT_FOUND);
        assertNothingChanged();
    }

    @Test
    void order_ofAFailedAttempt_failsWithAttemptNotPassed() {
        AssessmentAttempt attempt = addAttempt(List.of(0, 0, 0, 0, 0));

        assertFailure(order(ReevaluationSourceType.ATTEMPT, attempt.getId()),
                AssessmentPeerReviewError.ATTEMPT_NOT_PASSED);
        assertNothingChanged();
    }

    // ---------- Case ----------

    @Test
    void order_ofAnApprovedCase_reopensTheNodeAndGivesTheCaseToAnotherVerifier() {
        VerificationCase approved = addApprovedCase();
        profiles.save(new VerifierProfile(APPROVER_ID, "http-basics"));
        profiles.save(new VerifierProfile(OTHER_VERIFIER_ID, "http-basics"));

        Result<Reevaluation> result = order(ReevaluationSourceType.CASE, approved.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(learningPath.reopenedNodes()).containsExactly(NODE_ID);
        assertThat(approved.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(approved.getVerifierUserId()).isEqualTo(OTHER_VERIFIER_ID);
        assertThat(approved.getDecision()).isNull();
        assertThat(approved.getReevaluationCount()).isEqualTo(1);
        assertThat(approved.getReviewDueAt()).isNotNull();
        assertThat(result.value().getSourceType()).isEqualTo(ReevaluationSourceType.CASE);
        assertThat(result.value().getStudentId()).isEqualTo(STUDENT_ID);
    }

    @Test
    void order_ofAnApprovedCase_neverGivesItBackToTheVerifierWhoApprovedIt() {
        VerificationCase approved = addApprovedCase();
        profiles.save(new VerifierProfile(APPROVER_ID, "http-basics"));

        Result<Reevaluation> result = order(ReevaluationSourceType.CASE, approved.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(approved.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(approved.getVerifierUserId()).isNull();
    }

    @Test
    void order_ofACaseWithoutAnotherVerifier_leavesItPending() {
        VerificationCase approved = addApprovedCase();

        Result<Reevaluation> result = order(ReevaluationSourceType.CASE, approved.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(approved.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(reevaluations.reevaluations()).hasSize(1);
    }

    @Test
    void order_ofAnUnknownCase_failsWithCaseNotFound() {
        assertFailure(order(ReevaluationSourceType.CASE, 99), AssessmentPeerReviewError.CASE_NOT_FOUND);
        assertNothingChanged();
    }

    @Test
    void order_ofACaseThatIsNotApproved_failsWithCaseNotApproved() {
        VerificationCase rejected = cases.save(new VerificationCase(1, STUDENT_ID, NODE_ID, "http-basics",
                CaseType.QUIZ).assignVerifier(APPROVER_ID).resolve(ReviewDecision.REJECTED, "Not enough."));

        assertFailure(order(ReevaluationSourceType.CASE, rejected.getId()),
                AssessmentPeerReviewError.CASE_NOT_APPROVED);
        assertNothingChanged();
        assertThat(rejected.getStatus()).isEqualTo(CaseStatus.RESOLVED);
    }

    @Test
    void order_ofAnOpenCase_failsWithCaseNotApproved() {
        VerificationCase open = cases.save(new VerificationCase(1, STUDENT_ID, NODE_ID, "http-basics",
                CaseType.QUIZ).assignVerifier(APPROVER_ID));

        assertFailure(order(ReevaluationSourceType.CASE, open.getId()), AssessmentPeerReviewError.CASE_NOT_APPROVED);
        assertNothingChanged();
    }

    // ---------- Shared rules ----------

    @Test
    void order_byWhoIsNotASeniorOrNotAnEnabledVerifier_isForbidden() {
        AssessmentAttempt attempt = addPassedAttempt();
        profiles.save(new VerifierProfile(8, "http-basics"));
        seniors.add(9);

        assertFailure(service.handle(new OrderReevaluationCommand(8, ReevaluationSourceType.ATTEMPT,
                attempt.getId(), JUSTIFICATION)), AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertFailure(service.handle(new OrderReevaluationCommand(9, ReevaluationSourceType.ATTEMPT,
                attempt.getId(), JUSTIFICATION)), AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        profiles.findByUserId(SENIOR_ID).orElseThrow().revoke();
        assertFailure(order(ReevaluationSourceType.ATTEMPT, attempt.getId()),
                AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertNothingChanged();
    }

    @Test
    void order_ofTheOwnResultOfTheSenior_isForbidden() {
        AssessmentAttempt attempt = addPassedAttempt();
        profiles.save(new VerifierProfile(STUDENT_ID, "http-basics"));
        seniors.add(STUDENT_ID);

        Result<Reevaluation> result = service.handle(new OrderReevaluationCommand(STUDENT_ID,
                ReevaluationSourceType.ATTEMPT, attempt.getId(), JUSTIFICATION));

        assertFailure(result, AssessmentPeerReviewError.CANNOT_REEVALUATE_OWN_WORK);
        assertNothingChanged();
    }

    @Test
    void order_withoutAValidSourceType_failsWithInvalidSourceType() {
        assertFailure(service.handle(new OrderReevaluationCommand(SENIOR_ID, null, 1, JUSTIFICATION)),
                AssessmentPeerReviewError.INVALID_SOURCE_TYPE);
    }

    @Test
    void order_withoutOrWithATooLongJustification_fails() {
        AssessmentAttempt attempt = addPassedAttempt();

        for (String blank : new String[]{null, "", "   "}) {
            assertFailure(service.handle(new OrderReevaluationCommand(SENIOR_ID, ReevaluationSourceType.ATTEMPT,
                    attempt.getId(), blank)), AssessmentPeerReviewError.JUSTIFICATION_REQUIRED);
        }
        assertFailure(service.handle(new OrderReevaluationCommand(SENIOR_ID, ReevaluationSourceType.ATTEMPT,
                attempt.getId(), "j".repeat(Reevaluation.MAX_JUSTIFICATION_LENGTH + 1))),
                AssessmentPeerReviewError.JUSTIFICATION_TOO_LONG);
        assertNothingChanged();
    }

    @Test
    void order_whenTheNodeHasAnOpenCase_failsWithOpenCaseAlreadyExists() {
        AssessmentAttempt attempt = addPassedAttempt();
        cases.save(new VerificationCase(2, STUDENT_ID, NODE_ID, "http-basics", CaseType.QUIZ));

        assertFailure(order(ReevaluationSourceType.ATTEMPT, attempt.getId()),
                AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
        assertNothingChanged();
    }

    @Test
    void order_whenTheNodeCannotBeReopened_failsWithNodeNotReopenableAndKeepsTheCase() {
        VerificationCase approved = addApprovedCase();

        for (NodeReopenOutcome outcome : List.of(NodeReopenOutcome.NOT_COMPLETED,
                NodeReopenOutcome.COMPLETED_BY_CERTIFICATE, NodeReopenOutcome.NODE_NOT_FOUND)) {
            learningPath.setNextReopenOutcome(outcome);
            assertFailure(order(ReevaluationSourceType.CASE, approved.getId()),
                    AssessmentPeerReviewError.NODE_NOT_REOPENABLE);
        }

        assertNothingChanged();
        assertThat(approved.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(approved.getReevaluationCount()).isZero();
    }

    @Test
    void order_whenTheNodeCannotBeSaved_failsWithDatabaseError() {
        AssessmentAttempt attempt = addPassedAttempt();
        learningPath.setNextReopenOutcome(NodeReopenOutcome.FAILED);

        assertFailure(order(ReevaluationSourceType.ATTEMPT, attempt.getId()), AssessmentPeerReviewError.DATABASE_ERROR);
        assertNothingChanged();
    }

    @Test
    void order_whenPersistenceFails_failsWithDatabaseError() {
        AssessmentAttempt attempt = addPassedAttempt();
        reevaluations.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(order(ReevaluationSourceType.ATTEMPT, attempt.getId()), AssessmentPeerReviewError.DATABASE_ERROR);
    }
}
