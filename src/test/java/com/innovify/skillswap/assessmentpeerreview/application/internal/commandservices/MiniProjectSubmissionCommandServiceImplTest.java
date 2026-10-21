package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitMiniProjectOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
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
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
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

/** The mini-project 1 of the fake facade: node 10, student 1, "http-basics", due in five days. */
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
                new ReviewDeadlineResolver(policies, plans), events, counting, TestMessages.source());
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

    private Result<SubmitMiniProjectOutcome> submit(int miniProjectId, int studentId, String url) {
        return service.handle(new SubmitMiniProjectCommand(miniProjectId, studentId, url));
    }

    private Result<SubmitMiniProjectOutcome> submit() {
        return submit(MINI_PROJECT_ID, STUDENT_ID, URL);
    }

    private void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
        assertThat(submissions.submissions()).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    // ---------- Happy path ----------

    @Test
    void submit_savesTheDeliveryAndPublishesTheEvent() {
        Result<SubmitMiniProjectOutcome> result = submit();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value().submission();
        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getMiniProjectId()).isEqualTo(MINI_PROJECT_ID);
        assertThat(submission.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(submission.getPathNodeId()).isEqualTo(NODE_ID);
        assertThat(submission.getSkillTag()).isEqualTo("http-basics");
        assertThat(submission.getRepositoryUrl()).isEqualTo(URL);
        assertThat(submissions.submissions()).containsExactly(submission);

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

    // ---------- The verification case ----------

    @Test
    void submit_opensThePendingCaseOfTheDeliveryInOneTransaction() {
        Result<SubmitMiniProjectOutcome> result = submit();

        assertThat(result.value().escalationLimitReached()).isNull();
        VerificationCase opened = result.value().verificationCase();
        assertThat(cases.cases()).containsExactly(opened);
        assertThat(opened.getSubmissionId()).isEqualTo(result.value().submission().getId());
        assertThat(opened.getAttemptId()).isNull();
        assertThat(opened.getCaseType()).isEqualTo(CaseType.MINI_PROJECT);
        assertThat(opened.getEvidenceUrl()).isEqualTo(URL);
        assertThat(opened.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(opened.getPathNodeId()).isEqualTo(NODE_ID);
        assertThat(opened.getSkillTag()).isEqualTo("http-basics");
        assertThat(opened.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(opened.getVerifierUserId()).isNull();
        assertThat(transactions.get()).isEqualTo(1);
        assertThat(cases.lockedStudents()).containsExactly(STUDENT_ID);
    }

    @Test
    void submit_assignsTheCaseWhenAVerifierIsAvailable() {
        profiles.save(new VerifierProfile(2, "http-basics"));

        VerificationCase opened = submit().value().verificationCase();

        assertThat(opened.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(opened.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void submit_neverAssignsTheCaseToTheStudent() {
        profiles.save(new VerifierProfile(STUDENT_ID, "http-basics"));

        assertThat(submit().value().verificationCase().getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    @Test
    void submit_onTheFreePlan_opensTheCaseDueInFiveBusinessDays() {
        VerificationCase opened = submit().value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(ReviewDeadline.businessDays(5).dueFrom(opened.getOpenedAt()));
    }

    @Test
    void submit_onThePaidPlan_opensTheCaseDueIn48Hours() {
        plans.premium(STUDENT_ID);

        VerificationCase opened = submit().value().verificationCase();

        assertThat(opened.getReviewDueAt()).isEqualTo(opened.getOpenedAt().plus(Duration.ofHours(48)));
    }

    @Test
    void submit_onTheFreePlanAfterThreeEscalationsThisMonth_savesTheDeliveryButOpensNoCase() {
        seedCasesOpenedThisMonth(3);

        Result<SubmitMiniProjectOutcome> result = submit();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().submission().getId()).isNotNull();
        assertThat(submissions.submissions()).containsExactly(result.value().submission());
        assertThat(result.value().verificationCase()).isNull();
        assertThat(result.value().escalationLimitReached()).satisfies(limit -> {
            assertThat(limit.plan()).isEqualTo("Free");
            assertThat(limit.max()).isEqualTo(3);
            assertThat(limit.current()).isEqualTo(3);
            assertThat(limit.upgradeAvailable()).isTrue();
        });
        assertThat(cases.cases()).hasSize(3);
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void submit_onTheFreePlanWithTwoEscalationsThisMonth_stillOpensTheThird() {
        seedCasesOpenedThisMonth(2);

        Result<SubmitMiniProjectOutcome> result = submit();

        assertThat(result.value().verificationCase()).isNotNull();
        assertThat(result.value().escalationLimitReached()).isNull();
    }

    @Test
    void submit_casesOfPreviousMonthsDoNotCount() {
        seedCasesOpenedThisMonth(3);
        Instant lastMonth = EscalationCalendar.startOfMonth(Instant.now()).minus(Duration.ofDays(1));
        cases.cases().forEach(c -> ReflectionTestUtils.setField(c, "openedAt", lastMonth));

        assertThat(submit().value().verificationCase()).isNotNull();
    }

    @Test
    void submit_onThePaidPlan_allowsTenEscalationsAMonth() {
        plans.premium(STUDENT_ID);
        seedCasesOpenedThisMonth(9);
        assertThat(submit().value().verificationCase()).isNotNull();

        learningPath.addMiniProject(2, 11, STUDENT_ID, "http-basics", Instant.now().plus(Duration.ofDays(5)), true,
                true);
        Result<SubmitMiniProjectOutcome> eleventh = submit(2, STUDENT_ID, URL);

        assertThat(eleventh.value().verificationCase()).isNull();
        assertThat(eleventh.value().escalationLimitReached().max()).isEqualTo(10);
        assertThat(eleventh.value().escalationLimitReached().upgradeAvailable()).isFalse();
    }

    @Test
    void submit_stripsTheLink() {
        assertThat(submit(MINI_PROJECT_ID, STUDENT_ID, "  " + URL + " ").value().submission().getRepositoryUrl())
                .isEqualTo(URL);
    }

    // ---------- Rejections ----------

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
        MiniProjectSubmission first = submit().value().submission();
        cases.cases().clear();
        events.published().clear();

        Result<SubmitMiniProjectOutcome> second = submit(MINI_PROJECT_ID, STUDENT_ID, "https://github.com/ana/other");

        assertThat(second.isFailure()).isTrue();
        assertThat(second.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(submissions.submissions()).containsExactly(first);
        assertThat(events.published()).isEmpty();
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

        Result<SubmitMiniProjectOutcome> result = submit();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenTheDatabaseRejectsTheCaseAndNoDeliveryExists_returnsOpenCaseAlreadyExists() {
        submissions.failOnSave(new DataIntegrityViolationException("ux_verification_cases_open"));

        assertFailure(submit(), AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
    }

    @Test
    void submit_whenSomethingElseFails_returnsInternalServerError() {
        submissions.failOnSave(new IllegalStateException("boom"));

        assertFailure(submit(), AssessmentPeerReviewError.INTERNAL_SERVER_ERROR);
    }
}
