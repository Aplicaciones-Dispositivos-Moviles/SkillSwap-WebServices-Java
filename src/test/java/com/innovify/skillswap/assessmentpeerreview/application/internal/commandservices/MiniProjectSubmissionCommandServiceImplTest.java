package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectEvaluationOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectEvaluationService;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeReviewDeadlinePolicyRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeSubscriptionContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.EvaluateMiniProjectSubmissionCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectApprovedByAi;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectEscalatedByAi;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.MiniProjectBrief;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
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

/** The mini-project 1 of the fake facade: node 10, student 1, "http-basics", due in five days, passing score 70. */
class MiniProjectSubmissionCommandServiceImplTest {

    private static final int STUDENT_ID = 1;
    private static final int MINI_PROJECT_ID = 1;
    private static final int NODE_ID = 10;
    private static final String URL = "https://github.com/ana/http-basics";

    private FakeMiniProjectSubmissionRepository submissions = new FakeMiniProjectSubmissionRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeSubscriptionContextFacade plans = new FakeSubscriptionContextFacade();
    private final FakeReviewDeadlinePolicyRepository policies = new FakeReviewDeadlinePolicyRepository();
    private final FakeMiniProjectEvaluationService ai = new FakeMiniProjectEvaluationService();
    private final AtomicInteger transactions = new AtomicInteger();
    private MiniProjectSubmissionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = newService();
        learningPath.addMiniProject();
    }

    private MiniProjectSubmissionCommandServiceImpl newService() {
        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };
        return new MiniProjectSubmissionCommandServiceImpl(submissions, cases, learningPath,
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()), plans,
                new ReviewDeadlineResolver(policies, plans), ai, events, counting, TestMessages.source());
    }

    /** Cases the student already opened this month, on other nodes. */
    private void seedCasesOpenedThisMonth(int count) {
        for (int i = 0; i < count; i++) {
            cases.save(new VerificationCase(100 + i, STUDENT_ID, 50 + i, "sql-fundamentals", CaseType.QUIZ));
        }
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<MiniProjectSubmission> submit(int miniProjectId, int studentId, String url) {
        return service.handle(new SubmitMiniProjectCommand(miniProjectId, studentId, url));
    }

    private Result<MiniProjectSubmission> submit() {
        return submit(MINI_PROJECT_ID, STUDENT_ID, URL);
    }

    /** Delivers the mini-project 1 and runs its preliminary evaluation, as the event handler does. */
    private Result<MiniProjectEvaluationOutcome> deliverAndEvaluate() {
        MiniProjectSubmission delivered = submit().value();
        transactions.set(0);
        return evaluate(delivered.getId());
    }

    private Result<MiniProjectEvaluationOutcome> evaluate(int submissionId) {
        return service.handle(new EvaluateMiniProjectSubmissionCommand(submissionId));
    }

    private void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
        assertThat(submissions.submissions()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    // ---------- Delivery (US19 escenario 2) ----------

    @Test
    void submit_savesThePendingDeliveryAndPublishesTheEvent() {
        Result<MiniProjectSubmission> result = submit();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value();
        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getMiniProjectId()).isEqualTo(MINI_PROJECT_ID);
        assertThat(submission.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(submission.getPathNodeId()).isEqualTo(NODE_ID);
        assertThat(submission.getSkillTag()).isEqualTo("http-basics");
        assertThat(submission.getRepositoryUrl()).isEqualTo(URL);
        assertThat(submission.getEvaluationStatus()).isEqualTo(EvaluationStatus.PENDING);
        assertThat(submissions.submissions()).containsExactly(submission);
        assertThat(transactions.get()).isEqualTo(1);

        assertThat(events.published()).hasSize(1);
        MiniProjectSubmitted event = (MiniProjectSubmitted) events.published().get(0);
        assertThat(event.submissionId()).isEqualTo(submission.getId());
        assertThat(event.miniProjectId()).isEqualTo(MINI_PROJECT_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT_ID);
        assertThat(event.pathNodeId()).isEqualTo(NODE_ID);
        assertThat(event.skillTag()).isEqualTo("http-basics");
        assertThat(event.repositoryUrl()).isEqualTo(URL);
        assertThat(event.submittedAt()).isEqualTo(submission.getSubmittedAt());
    }

    @Test
    void submit_opensNoCaseAndDoesNotAskTheAi_theEventHandlerDoes() {
        submit();

        assertThat(cases.cases()).isEmpty();
        assertThat(cases.lockedStudents()).isEmpty();
        assertThat(ai.requests()).isEmpty();
        assertThat(learningPath.completedNodes()).isEmpty();
    }

    @Test
    void submit_stripsTheLink() {
        assertThat(submit(MINI_PROJECT_ID, STUDENT_ID, "  " + URL + " ").value().getRepositoryUrl()).isEqualTo(URL);
    }

    // ---------- Rejections of the delivery ----------

    @Test
    void submit_forAnUnknownMiniProject_returnsMiniProjectNotFound() {
        assertFailure(submit(99, STUDENT_ID, URL), AssessmentPeerReviewError.MINI_PROJECT_NOT_FOUND);
    }

    @Test
    void submit_forAnotherStudentsMiniProject_returnsNotMiniProjectOwner() {
        assertFailure(submit(MINI_PROJECT_ID, 2, URL), AssessmentPeerReviewError.NOT_MINI_PROJECT_OWNER);
    }

    @Test
    void submit_withAnInvalidLink_returnsInvalidRepositoryUrl() {
        for (String url : new String[]{null, "", "   ", "github.com/ana/repo", "ftp://github.com/ana/repo",
                "https://" + "a".repeat(600)}) {
            assertFailure(submit(MINI_PROJECT_ID, STUDENT_ID, url), AssessmentPeerReviewError.INVALID_REPOSITORY_URL);
        }
    }

    @Test
    void submit_whenTheNodeIsNotAvailable_returnsNodeNotAvailable() {
        learningPath.addMiniProject(MINI_PROJECT_ID, NODE_ID, STUDENT_ID, "http-basics",
                Instant.now().plus(Duration.ofDays(5)), true, false);

        assertFailure(submit(), AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
    }

    @Test
    void submit_whenThereIsANewerMiniProject_returnsMiniProjectOutdated() {
        learningPath.addMiniProject(MINI_PROJECT_ID, NODE_ID, STUDENT_ID, "http-basics",
                Instant.now().plus(Duration.ofDays(5)), false, true);

        assertFailure(submit(), AssessmentPeerReviewError.MINI_PROJECT_OUTDATED);
    }

    @Test
    void submit_afterTheDeadline_returnsMiniProjectExpired() {
        learningPath.addMiniProject(MINI_PROJECT_ID, NODE_ID, STUDENT_ID, "http-basics",
                Instant.now().minusSeconds(1), true, true);

        assertFailure(submit(), AssessmentPeerReviewError.MINI_PROJECT_EXPIRED);
    }

    @Test
    void submit_whenTheNodeHasAnOpenCase_returnsOpenCaseAlreadyExists() {
        cases.save(new VerificationCase(1, STUDENT_ID, NODE_ID, "http-basics", CaseType.QUIZ));

        assertFailure(submit(), AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
    }

    @Test
    void submit_twice_returnsMiniProjectAlreadySubmittedAndKeepsTheFirstDelivery() {
        MiniProjectSubmission first = submit().value();
        events.published().clear();

        Result<MiniProjectSubmission> second = submit(MINI_PROJECT_ID, STUDENT_ID, "https://github.com/ana/other");

        assertThat(second.isFailure()).isTrue();
        assertThat(second.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(submissions.submissions()).containsExactly(first);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_again_afterItsEvaluationOpenedTheCaseOrCompletedTheNode_returnsMiniProjectAlreadySubmitted() {
        deliverAndEvaluate();
        assertThat(cases.cases()).hasSize(1);
        Result<MiniProjectSubmission> afterTheCase = submit();

        learningPath.addMiniProject(MINI_PROJECT_ID, NODE_ID, STUDENT_ID, "http-basics",
                Instant.now().plus(Duration.ofDays(5)), true, false);
        Result<MiniProjectSubmission> afterTheNode = submit();

        assertThat(afterTheCase.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(afterTheNode.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
    }

    @Test
    void submit_whenTheDatabaseRejectsTheDeliveryAndItExists_returnsMiniProjectAlreadySubmitted() {
        // The other request got its delivery in between: the re-query finds it.
        submissions = new FakeMiniProjectSubmissionRepository() {
            @Override
            public MiniProjectSubmission save(MiniProjectSubmission submission) {
                super.save(submission);
                throw new DataIntegrityViolationException("uq_mini_project_submissions_mini_project");
            }
        };
        service = newService();

        Result<MiniProjectSubmission> result = submit();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenTheDatabaseRejectsTheDeliveryForAnotherReason_returnsDatabaseError() {
        submissions.failOnSave(new DataIntegrityViolationException("ck_mini_project_submissions_evaluation_status"));

        assertFailure(submit(), AssessmentPeerReviewError.DATABASE_ERROR);
    }

    @Test
    void submit_whenSomethingElseFails_returnsInternalServerError() {
        submissions.failOnSave(new IllegalStateException("boom"));

        assertFailure(submit(), AssessmentPeerReviewError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Preliminary evaluation: approved by the AI (US19 escenario 2) ----------

    @Test
    void evaluate_asksTheAiWithTheRubricOfTheMiniProjectAndTheRepository() {
        deliverAndEvaluate();

        assertThat(ai.requests()).hasSize(1);
        MiniProjectBrief brief = ai.requests().get(0).brief();
        assertThat(ai.requests().get(0).repositoryUrl()).isEqualTo(URL);
        assertThat(brief.skillTag()).isEqualTo("http-basics");
        assertThat(brief.title()).isEqualTo("Mini-project 1");
        assertThat(brief.statement()).isNotBlank();
        assertThat(brief.passingScore()).isEqualTo(70);
        assertThat(brief.rubric()).extracting(MiniProjectBrief.Criterion::name)
                .containsExactly("Functionality", "Code quality", "Documentation", "Version control");
        assertThat(brief.rubric()).extracting(MiniProjectBrief.Criterion::weight).containsExactly(40, 30, 20, 10);
    }

    @Test
    void evaluate_sufficient_approvesTheDeliveryCompletesTheNodeAndOpensNoCase() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value().submission();
        assertThat(submission.getEvaluationStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(submission.getPreliminaryScore()).isEqualTo(85);
        assertThat(submission.getPreliminaryConfidence()).isEqualTo(90);
        assertThat(submission.getPreliminaryFeedback()).isEqualTo("Well structured and tested.");
        assertThat(result.value().verificationCase()).isNull();
        assertThat(result.value().escalationLimitReached()).isNull();
        assertThat(learningPath.completedNodes()).containsExactly(NODE_ID);
        assertThat(cases.cases()).isEmpty();
        assertThat(cases.lockedStudents()).isEmpty();
        assertThat(submissions.lockedSubmissions()).containsExactly(submission.getId());
        assertThat(transactions.get()).isEqualTo(1);
    }

    @Test
    void evaluate_sufficient_withTheNodeAlreadyCompleted_stillApproves() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        learningPath.setNextOutcome(NodeCompletionOutcome.ALREADY_COMPLETED);

        assertThat(deliverAndEvaluate().value().submission().getEvaluationStatus())
                .isEqualTo(EvaluationStatus.APPROVED);
    }

    @Test
    void evaluate_sufficient_whenTheNodeCannotBeCompleted_failsAndKeepsTheDeliveryPending() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        learningPath.setNextOutcome(NodeCompletionOutcome.NODE_LOCKED);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
        assertThat(submissions.submissions().get(0).isAwaitingEvaluation()).isTrue();
    }

    // ---------- Preliminary evaluation: a verifier reviews it (US19 escenario 3) ----------

    @Test
    void evaluate_belowThePassingScore_opensThePendingMiniProjectCaseOfTheDelivery() {
        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value().submission();
        assertThat(submission.getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(submission.getPreliminaryScore()).isEqualTo(55);
        assertThat(submission.getPreliminaryConfidence()).isEqualTo(80);
        assertThat(result.value().escalationLimitReached()).isNull();

        VerificationCase opened = result.value().verificationCase();
        assertThat(cases.cases()).containsExactly(opened);
        assertThat(opened.getSubmissionId()).isEqualTo(submission.getId());
        assertThat(opened.getAttemptId()).isNull();
        assertThat(opened.getCaseType()).isEqualTo(CaseType.MINI_PROJECT);
        assertThat(opened.getEvidenceUrl()).isEqualTo(URL);
        assertThat(opened.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(opened.getPathNodeId()).isEqualTo(NODE_ID);
        assertThat(opened.getSkillTag()).isEqualTo("http-basics");
        assertThat(opened.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(opened.getVerifierUserId()).isNull();
        assertThat(learningPath.completedNodes()).isEmpty();
        assertThat(transactions.get()).isEqualTo(1);
        assertThat(cases.lockedStudents()).containsExactly(STUDENT_ID);
    }

    @Test
    void evaluate_withALowConfidence_opensTheCaseEvenWithAHighScore() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.UNSURE);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.value().submission().getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(result.value().submission().getPreliminaryScore()).isEqualTo(90);
        assertThat(result.value().verificationCase()).isNotNull();
        assertThat(learningPath.completedNodes()).isEmpty();
    }

    @Test
    void evaluate_exactlyAtThePassingScoreAndTheMinimumConfidence_approves() {
        ai.returnEvaluation(new PreliminaryEvaluation(70, PreliminaryEvaluation.MIN_CONFIDENCE, ""));

        assertThat(deliverAndEvaluate().value().submission().getEvaluationStatus())
                .isEqualTo(EvaluationStatus.APPROVED);
    }

    @Test
    void evaluate_whenTheAiFails_treatsItAsALowConfidenceAndOpensTheCase() {
        ai.failWith(new IllegalStateException("Gemini is unavailable"));

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value().submission();
        assertThat(submission.getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(submission.getPreliminaryScore()).isNull();
        assertThat(submission.getPreliminaryConfidence()).isNull();
        assertThat(submission.getEvaluatedAt()).isNotNull();
        assertThat(result.value().verificationCase()).isNotNull();
    }

    @Test
    void evaluate_whenTheMiniProjectNoLongerExists_opensTheCaseWithoutAskingTheAi() {
        MiniProjectSubmission delivered = submit().value();
        ReflectionTestUtils.setField(delivered, "miniProjectId", 99);

        Result<MiniProjectEvaluationOutcome> result = evaluate(delivered.getId());

        assertThat(ai.requests()).isEmpty();
        assertThat(result.value().submission().getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(result.value().verificationCase()).isNotNull();
    }

    @Test
    void evaluate_assignsTheCaseWhenAVerifierIsAvailable_neverToTheStudent() {
        profiles.save(new VerifierProfile(STUDENT_ID, "http-basics"));
        profiles.save(new VerifierProfile(2, "http-basics"));

        VerificationCase opened = deliverAndEvaluate().value().verificationCase();

        assertThat(opened.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(opened.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void evaluate_onTheFreePlan_opensTheCaseDueInFiveBusinessDays() {
        VerificationCase opened = deliverAndEvaluate().value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(ReviewDeadline.businessDays(5).dueFrom(opened.getOpenedAt()));
    }

    @Test
    void evaluate_onThePaidPlan_opensTheCaseDueIn48Hours() {
        plans.premium(STUDENT_ID);

        VerificationCase opened = deliverAndEvaluate().value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(opened.getOpenedAt().plus(Duration.ofHours(48)));
    }

    @Test
    void evaluate_onTheFreePlanAfterThreeEscalationsThisMonth_keepsTheDeliveryWithoutReview() {
        seedCasesOpenedThisMonth(3);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().submission().getEvaluationStatus())
                .isEqualTo(EvaluationStatus.ESCALATION_LIMIT_REACHED);
        assertThat(result.value().submission().getPreliminaryScore()).isEqualTo(55);
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
    void evaluate_approvedByTheAi_doesNotUseAnEscalation() {
        seedCasesOpenedThisMonth(3);
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);

        assertThat(deliverAndEvaluate().value().submission().getEvaluationStatus())
                .isEqualTo(EvaluationStatus.APPROVED);
    }

    @Test
    void evaluate_onTheFreePlanWithTwoEscalationsThisMonth_stillOpensTheThird() {
        seedCasesOpenedThisMonth(2);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.value().verificationCase()).isNotNull();
        assertThat(result.value().escalationLimitReached()).isNull();
    }

    @Test
    void evaluate_casesOfPreviousMonthsDoNotCount() {
        seedCasesOpenedThisMonth(3);
        Instant lastMonth = EscalationCalendar.startOfMonth(Instant.now()).minus(Duration.ofDays(1));
        cases.cases().forEach(c -> ReflectionTestUtils.setField(c, "openedAt", lastMonth));

        assertThat(deliverAndEvaluate().value().verificationCase()).isNotNull();
    }

    @Test
    void evaluate_onThePaidPlan_allowsTenEscalationsAMonth() {
        plans.premium(STUDENT_ID);
        seedCasesOpenedThisMonth(9);
        assertThat(deliverAndEvaluate().value().verificationCase()).isNotNull();

        learningPath.addMiniProject(2, 11, STUDENT_ID, "http-basics", Instant.now().plus(Duration.ofDays(5)), true,
                true);
        int second = submit(2, STUDENT_ID, URL).value().getId();
        Result<MiniProjectEvaluationOutcome> eleventh = evaluate(second);

        assertThat(eleventh.value().verificationCase()).isNull();
        assertThat(eleventh.value().escalationLimitReached().max()).isEqualTo(10);
        assertThat(eleventh.value().escalationLimitReached().upgradeAvailable()).isFalse();
    }

    // ---------- Preliminary evaluation: rejections ----------

    @Test
    void evaluate_anUnknownDelivery_returnsMiniProjectSubmissionNotFound() {
        Result<MiniProjectEvaluationOutcome> result = evaluate(99);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_SUBMISSION_NOT_FOUND);
        assertThat(ai.requests()).isEmpty();
    }

    @Test
    void evaluate_twice_returnsMiniProjectAlreadyEvaluatedAndOpensOneCase() {
        int id = deliverAndEvaluate().value().submission().getId();

        Result<MiniProjectEvaluationOutcome> again = evaluate(id);

        assertThat(again.isFailure()).isTrue();
        assertThat(again.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_EVALUATED);
        assertThat(cases.cases()).hasSize(1);
        assertThat(ai.requests()).hasSize(1);
    }

    @Test
    void evaluate_whenAnotherEvaluationSettledItWhileTheAiAnswered_savesNothing() {
        MiniProjectSubmission delivered = submit().value();
        // The other evaluation commits while this one waits for the AI: the locked re-read sees it.
        submissions = new FakeMiniProjectSubmissionRepository() {
            @Override
            public java.util.Optional<MiniProjectSubmission> findById(int id) {
                return java.util.Optional.of(delivered);
            }

            @Override
            public java.util.Optional<MiniProjectSubmission> findByIdForUpdate(int id) {
                delivered.escalate(null);
                return java.util.Optional.of(delivered);
            }
        };
        service = newService();

        Result<MiniProjectEvaluationOutcome> result = evaluate(delivered.getId());

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_EVALUATED);
        assertThat(cases.cases()).isEmpty();
        assertThat(learningPath.completedNodes()).isEmpty();
    }

    @Test
    void evaluate_whenTheNodeGotACaseAfterTheDelivery_doesNotAskTheAiNorOpenASecondCase() {
        MiniProjectSubmission delivered = submit().value();
        cases.save(new VerificationCase(1, STUDENT_ID, NODE_ID, "http-basics", CaseType.QUIZ));

        Result<MiniProjectEvaluationOutcome> result = evaluate(delivered.getId());

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
        assertThat(ai.requests()).isEmpty();
        assertThat(cases.cases()).hasSize(1);
        assertThat(delivered.isAwaitingEvaluation()).isTrue();
    }

    @Test
    void evaluate_whenSavingTheCaseFails_returnsDatabaseErrorAndKeepsTheDeliveryPending() {
        MiniProjectSubmission delivered = submit().value();
        cases.failOnSave(new DataIntegrityViolationException("ux_verification_cases_open"));

        Result<MiniProjectEvaluationOutcome> result = evaluate(delivered.getId());

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(delivered.isAwaitingEvaluation()).isTrue();
    }

    // ---------- Announcing the approval of the AI to the other contexts ----------

    private List<MiniProjectApprovedByAi> approvals() {
        return events.published().stream().filter(MiniProjectApprovedByAi.class::isInstance)
                .map(MiniProjectApprovedByAi.class::cast).toList();
    }

    @Test
    void evaluate_sufficient_announcesTheApprovalOnceTheNodeIsCompleted() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        MiniProjectSubmission submission = result.value().submission();
        assertThat(result.value().nodeCompleted()).isTrue();
        assertThat(approvals()).containsExactly(new MiniProjectApprovedByAi(submission.getId(), STUDENT_ID, NODE_ID,
                "http-basics"));
    }

    @Test
    void evaluate_sufficient_withTheNodeAlreadyCompleted_announcesNothing() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        learningPath.setNextOutcome(NodeCompletionOutcome.ALREADY_COMPLETED);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.value().submission().getEvaluationStatus()).isEqualTo(EvaluationStatus.APPROVED);
        assertThat(result.value().nodeCompleted()).isFalse();
        assertThat(approvals()).isEmpty();
    }

    @Test
    void evaluate_whenAVerifierMustReviewIt_announcesNothing() {
        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.value().nodeCompleted()).isFalse();
        assertThat(approvals()).isEmpty();
    }

    @Test
    void evaluate_whenTheNodeCannotBeCompleted_announcesNothing() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);
        learningPath.setNextOutcome(NodeCompletionOutcome.NODE_LOCKED);

        deliverAndEvaluate();

        assertThat(approvals()).isEmpty();
    }

    private List<MiniProjectEscalatedByAi> escalations() {
        return events.published().stream().filter(MiniProjectEscalatedByAi.class::isInstance)
                .map(MiniProjectEscalatedByAi.class::cast).toList();
    }

    @Test
    void evaluate_whenAVerifierMustReviewIt_announcesTheEscalationWithTheCase() {
        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        MiniProjectSubmission submission = result.value().submission();
        assertThat(escalations()).containsExactly(new MiniProjectEscalatedByAi(submission.getId(), STUDENT_ID,
                NODE_ID, "http-basics", result.value().verificationCase().getId()));
    }

    @Test
    void evaluate_whenTheAiApproves_announcesNoEscalation() {
        ai.returnEvaluation(FakeMiniProjectEvaluationService.ENOUGH);

        deliverAndEvaluate();

        assertThat(escalations()).isEmpty();
    }

    @Test
    void evaluate_whenThePlanHasNoEscalationsLeft_announcesNoEscalation() {
        seedCasesOpenedThisMonth(3);

        Result<MiniProjectEvaluationOutcome> result = deliverAndEvaluate();

        assertThat(result.value().submission().getEvaluationStatus())
                .isEqualTo(EvaluationStatus.ESCALATION_LIMIT_REACHED);
        assertThat(escalations()).isEmpty();
    }
}
