package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitAssessmentAttemptOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** Blueprint 1 belongs to student 1, assesses node 10 and has the correct answers 1, 2, 3, 0, 1. */
class AssessmentAttemptCommandServiceImplTest {

    private static final List<Integer> PASSING = List.of(1, 2, 3, 0, 1);
    private static final List<Integer> FAILING = List.of(0, 0, 0, 1, 0);

    private final FakeAssessmentAttemptRepository attempts = new FakeAssessmentAttemptRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final FakeSubscriptionContextFacade plans = new FakeSubscriptionContextFacade();
    private final AtomicInteger transactions = new AtomicInteger();
    private AssessmentAttemptCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };

        learningPath.addBlueprint();
        LocaleContextHolder.setLocale(Locale.US);
        service = new AssessmentAttemptCommandServiceImpl(attempts, cases, learningPath,
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()), plans, events, counting,
                TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<SubmitAssessmentAttemptOutcome> submit(List<Integer> answers, int blueprintId, int studentId) {
        return service.handle(new SubmitAssessmentAttemptCommand(studentId, blueprintId, answers));
    }

    private Result<SubmitAssessmentAttemptOutcome> submit(List<Integer> answers) {
        return submit(answers, 1, 1);
    }

    private static void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank();
    }

    // ---------- Approved attempt ----------

    @Test
    void submit_withPassingAnswers_passesCompletesTheNodeAndPublishesTheEvent() {
        Result<SubmitAssessmentAttemptOutcome> result = submit(PASSING);

        assertThat(result.isSuccess()).isTrue();
        var attempt = result.value().attempt();
        assertThat(attempt.isPassed()).isTrue();
        assertThat(attempt.getScore().value()).isEqualTo(5);
        assertThat(result.value().verificationCase()).isNull();
        assertThat(learningPath.completedNodes()).containsExactly(10);
        assertThat(cases.cases()).isEmpty();
        assertThat(transactions.get()).isEqualTo(1);
        assertThat(events.published()).hasSize(1);
        var published = (AssessmentAttemptPassed) events.published().get(0);
        assertThat(published.attemptId()).isEqualTo(attempt.getId());
        assertThat(published.studentId()).isEqualTo(1);
        assertThat(published.pathNodeId()).isEqualTo(10);
        assertThat(published.skillTag()).isEqualTo("http-basics");
    }

    @Test
    void submit_withExactlyTheThreshold_passes() {
        Result<SubmitAssessmentAttemptOutcome> result = submit(List.of(1, 2, 3, 0, 3));

        assertThat(result.value().attempt().isPassed()).isTrue();
        assertThat(result.value().attempt().getScore().value()).isEqualTo(4);
    }

    // ---------- Failed attempt ----------

    @Test
    void submit_withFailingAnswers_opensAndAssignsACase() {
        profiles.save(new VerifierProfile(2, "http-basics"));

        Result<SubmitAssessmentAttemptOutcome> result = submit(FAILING);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().attempt().isPassed()).isFalse();
        VerificationCase verificationCase = result.value().verificationCase();
        assertThat(cases.cases()).containsExactly(verificationCase);
        assertThat(verificationCase.getAttemptId()).isEqualTo(result.value().attempt().getId());
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(verificationCase.getVerifierUserId()).isEqualTo(2);
        assertThat(verificationCase.getPathNodeId()).isEqualTo(10);
        assertThat(verificationCase.getCaseType()).isEqualTo(CaseType.QUIZ);
        assertThat(learningPath.completedNodes()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_withFailingAnswersAndNoVerifiers_leavesTheCasePending() {
        Result<SubmitAssessmentAttemptOutcome> result = submit(FAILING);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().verificationCase().getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(result.value().verificationCase().getVerifierUserId()).isNull();
    }

    @Test
    void submit_neverAssignsTheCaseToTheStudent() {
        profiles.save(new VerifierProfile(1, "http-basics"));

        Result<SubmitAssessmentAttemptOutcome> result = submit(FAILING);

        assertThat(result.value().verificationCase().getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    // ---------- Plan: monthly escalations and review deadline ----------

    /** Cases the student 1 already opened this month, on other nodes. */
    private void seedCasesOpenedThisMonth(int count) {
        for (int i = 0; i < count; i++) {
            cases.save(new VerificationCase(100 + i, 1, 50 + i, "sql-fundamentals", CaseType.QUIZ));
        }
    }

    @Test
    void submit_onTheFreePlan_opensTheCaseDueInFiveBusinessDays() {
        Instant before = Instant.now();

        VerificationCase opened = submit(FAILING).value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(ReviewDeadline.businessDays(5).dueFrom(opened.getOpenedAt()));
        assertThat(opened.getReviewDueAt()).isAfterOrEqualTo(before.plus(Duration.ofDays(5)));
        assertThat(cases.lockedStudents()).containsExactly(1);
    }

    @Test
    void submit_onThePaidPlan_opensTheCaseDueIn48Hours() {
        plans.premium(1);

        VerificationCase opened = submit(FAILING).value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(opened.getOpenedAt().plus(Duration.ofHours(48)));
    }

    @Test
    void submit_onTheFreePlanAfterThreeEscalationsThisMonth_recordsTheAttemptButOpensNoCase() {
        seedCasesOpenedThisMonth(3);

        Result<SubmitAssessmentAttemptOutcome> result = submit(FAILING);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().attempt().getId()).isNotNull();
        assertThat(result.value().attempt().isPassed()).isFalse();
        assertThat(attempts.attempts()).hasSize(1);
        assertThat(result.value().verificationCase()).isNull();
        assertThat(result.value().escalationLimitReached()).satisfies(limit -> {
            assertThat(limit.plan()).isEqualTo("Free");
            assertThat(limit.max()).isEqualTo(3);
            assertThat(limit.current()).isEqualTo(3);
            assertThat(limit.upgradeAvailable()).isTrue();
        });
        assertThat(cases.cases()).hasSize(3);
    }

    @Test
    void submit_onTheFreePlanWithTwoEscalationsThisMonth_stillOpensTheThird() {
        seedCasesOpenedThisMonth(2);

        Result<SubmitAssessmentAttemptOutcome> result = submit(FAILING);

        assertThat(result.value().verificationCase()).isNotNull();
        assertThat(result.value().escalationLimitReached()).isNull();
    }

    @Test
    void submit_casesOfPreviousMonthsDoNotCount() {
        seedCasesOpenedThisMonth(3);
        Instant lastMonth = EscalationCalendar.startOfMonth(Instant.now()).minus(Duration.ofDays(1));
        cases.cases().forEach(c -> ReflectionTestUtils.setField(c, "openedAt", lastMonth));

        assertThat(submit(FAILING).value().verificationCase()).isNotNull();
    }

    @Test
    void submit_onThePaidPlan_allowsTenEscalationsAMonth() {
        plans.premium(1);
        seedCasesOpenedThisMonth(9);

        assertThat(submit(FAILING).value().verificationCase()).isNotNull();

        learningPath.addBlueprint(2, 11, 1, "http-basics", true, true);
        Result<SubmitAssessmentAttemptOutcome> eleventh = submit(FAILING, 2, 1);
        assertThat(eleventh.value().verificationCase()).isNull();
        assertThat(eleventh.value().escalationLimitReached().max()).isEqualTo(10);
        assertThat(eleventh.value().escalationLimitReached().upgradeAvailable()).isFalse();
    }

    @Test
    void submit_aPassingAttempt_needsNoEscalation() {
        seedCasesOpenedThisMonth(3);

        Result<SubmitAssessmentAttemptOutcome> result = submit(PASSING);

        assertThat(result.value().attempt().isPassed()).isTrue();
        assertThat(result.value().escalationLimitReached()).isNull();
        assertThat(cases.lockedStudents()).isEmpty();
    }

    // ---------- Rejections ----------

    @Test
    void submit_forAnUnknownBlueprint_failsWithBlueprintNotFound() {
        assertFailure(submit(PASSING, 99, 1), AssessmentPeerReviewError.BLUEPRINT_NOT_FOUND);
    }

    @Test
    void submit_forAnotherStudentsBlueprint_failsWithNotBlueprintOwner() {
        assertFailure(submit(PASSING, 1, 2), AssessmentPeerReviewError.NOT_BLUEPRINT_OWNER);
        assertThat(attempts.attempts()).isEmpty();
    }

    @Test
    void submit_withTheWrongNumberOfAnswers_failsWithInvalidAnswers() {
        assertFailure(submit(List.of(1, 2, 3)), AssessmentPeerReviewError.INVALID_ANSWERS);
    }

    @Test
    void submit_withoutAnswers_failsWithInvalidAnswers() {
        assertFailure(submit(null), AssessmentPeerReviewError.INVALID_ANSWERS);
    }

    @Test
    void submit_withAnAnswerOutOfRange_failsWithInvalidAnswers() {
        assertFailure(submit(List.of(1, 2, 3, 0, 4)), AssessmentPeerReviewError.INVALID_ANSWERS);
        assertFailure(submit(List.of(1, 2, 3, 0, -1)), AssessmentPeerReviewError.INVALID_ANSWERS);
    }

    @Test
    void submit_whenTheNodeIsNotAvailable_failsWithNodeNotAvailable() {
        learningPath.addBlueprint(true, false);

        assertFailure(submit(PASSING), AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
    }

    @Test
    void submit_whenThereIsAnOpenCaseForTheNode_failsWithOpenCaseAlreadyExists() {
        cases.save(new VerificationCase(5, 1, 10, "http-basics", CaseType.QUIZ));

        assertFailure(submit(PASSING), AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
        assertThat(attempts.attempts()).isEmpty();
    }

    @Test
    void submit_whenTheCaseOfTheNodeWasResolved_isAllowed() {
        cases.save(new VerificationCase(5, 1, 10, "http-basics", CaseType.QUIZ).assignVerifier(2)
                .resolve(ReviewDecision.REJECTED, "Needs work."));

        assertThat(submit(PASSING).isSuccess()).isTrue();
    }

    @Test
    void submit_forAnOutdatedBlueprint_failsWithBlueprintOutdated() {
        learningPath.addBlueprint(false, true);

        assertFailure(submit(PASSING), AssessmentPeerReviewError.BLUEPRINT_OUTDATED);
    }

    @Test
    void submit_twiceForTheSameBlueprint_failsWithAttemptAlreadySubmitted() {
        submit(PASSING);

        assertFailure(submit(PASSING), AssessmentPeerReviewError.ATTEMPT_ALREADY_SUBMITTED);
        assertThat(attempts.attempts()).hasSize(1);
    }

    // ---------- Failures ----------

    @Test
    void submit_whenTheNodeCannotBeCompleted_failsAndPublishesNothing() {
        learningPath.setNextOutcome(NodeCompletionOutcome.NODE_LOCKED);

        assertFailure(submit(PASSING), AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenCompletingTheNodeFails_failsWithDatabaseError() {
        learningPath.setNextOutcome(NodeCompletionOutcome.FAILED);

        assertFailure(submit(PASSING), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenPersistenceFails_failsWithDatabaseError() {
        attempts.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(submit(PASSING), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenTheCaseCannotBeSaved_failsWithDatabaseError() {
        cases.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(submit(FAILING), AssessmentPeerReviewError.DATABASE_ERROR);
    }

    @Test
    void submit_whenSomethingUnexpectedFails_failsWithInternalServerError() {
        attempts.failOnSave(new IllegalStateException("boom"));

        assertFailure(submit(PASSING), AssessmentPeerReviewError.INTERNAL_SERVER_ERROR);
    }
}
