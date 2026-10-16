package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;

/** The mini-project 1 of the fake facade: node 10, student 1, "http-basics", due in five days. */
class MiniProjectSubmissionCommandServiceImplTest {

    private static final int STUDENT_ID = 1;
    private static final int MINI_PROJECT_ID = 1;
    private static final int NODE_ID = 10;
    private static final String URL = "https://github.com/ana/http-basics";

    private final FakeMiniProjectSubmissionRepository submissions = new FakeMiniProjectSubmissionRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private MiniProjectSubmissionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new MiniProjectSubmissionCommandServiceImpl(submissions, cases, learningPath, events,
                TestMessages.source());
        learningPath.addMiniProject();
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
        Result<MiniProjectSubmission> result = submit();

        assertThat(result.isSuccess()).isTrue();
        MiniProjectSubmission submission = result.value();
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

    @Test
    void submit_doesNotOpenACase() {
        submit();

        assertThat(cases.findOpenByStudentAndNode(STUDENT_ID, NODE_ID)).isEmpty();
    }

    @Test
    void submit_stripsTheLink() {
        assertThat(submit(MINI_PROJECT_ID, STUDENT_ID, "  " + URL + " ").value().getRepositoryUrl()).isEqualTo(URL);
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
        MiniProjectSubmission first = submit().value();
        events.published().clear();

        Result<MiniProjectSubmission> second = submit(MINI_PROJECT_ID, STUDENT_ID, "https://github.com/ana/other");

        assertThat(second.isFailure()).isTrue();
        assertThat(second.error()).isEqualTo(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
        assertThat(submissions.submissions()).containsExactly(first);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void submit_whenTheDatabaseRejectsADuplicate_returnsMiniProjectAlreadySubmitted() {
        submissions.failOnSave(new DataIntegrityViolationException("uq_mini_project_submissions_mini_project"));

        assertFailure(submit(), AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
    }

    @Test
    void submit_whenSomethingElseFails_returnsInternalServerError() {
        submissions.failOnSave(new IllegalStateException("boom"));

        assertFailure(submit(), AssessmentPeerReviewError.INTERNAL_SERVER_ERROR);
    }
}
