package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitAssessmentAttemptOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
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
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
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
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()), events, counting,
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
        cases.save(new VerificationCase(5, 1, 10, "http-basics"));

        assertFailure(submit(PASSING), AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
        assertThat(attempts.attempts()).isEmpty();
    }

    @Test
    void submit_whenTheCaseOfTheNodeWasResolved_isAllowed() {
        cases.save(new VerificationCase(5, 1, 10, "http-basics").assignVerifier(2)
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
