package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FailedQuestion;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCasesByVerifierQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerifierProfileByUserIdQuery;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueryServicesImplTest {

    private final FakeAssessmentAttemptRepository attempts = new FakeAssessmentAttemptRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();

    private VerificationCaseQueryServiceImpl caseService() {
        return new VerificationCaseQueryServiceImpl(cases, attempts, learningPath);
    }

    private AssessmentAttempt addAttempt(List<Integer> answers) {
        return attempts.save(new AssessmentAttempt(1, 1, answers, FakeLearningPathContextFacade.CORRECT));
    }

    // ---------- Attempts and profiles ----------

    @Test
    void attemptQuery_returnsTheAttemptOrEmpty() {
        AssessmentAttempt attempt = addAttempt(List.of(1, 2, 3, 0, 1));
        var service = new AssessmentAttemptQueryServiceImpl(attempts);

        assertThat(service.handle(new GetAssessmentAttemptByIdQuery(attempt.getId()))).containsSame(attempt);
        assertThat(service.handle(new GetAssessmentAttemptByIdQuery(99))).isEmpty();
    }

    @Test
    void profileQuery_returnsTheProfileOfTheUserOrEmpty() {
        VerifierProfile profile = profiles.save(new VerifierProfile(2, "http-basics"));
        var service = new VerifierProfileQueryServiceImpl(profiles);

        assertThat(service.handle(new GetVerifierProfileByUserIdQuery(2))).containsSame(profile);
        assertThat(service.handle(new GetVerifierProfileByUserIdQuery(3))).isEmpty();
    }

    // ---------- Cases ----------

    @Test
    void caseQuery_byId_returnsTheCaseOrEmpty() {
        VerificationCase verificationCase = cases.save(new VerificationCase(1, 1, 10, "http-basics"));

        assertThat(caseService().handle(new GetVerificationCaseByIdQuery(verificationCase.getId())))
                .containsSame(verificationCase);
        assertThat(caseService().handle(new GetVerificationCaseByIdQuery(99))).isEmpty();
    }

    @Test
    void caseQuery_byVerifier_returnsOnlyTheirCasesNewestFirst() {
        VerificationCase first = cases.save(new VerificationCase(1, 1, 10, "http-basics").assignVerifier(2));
        cases.save(new VerificationCase(2, 1, 11, "http-basics").assignVerifier(3));
        VerificationCase second = cases.save(new VerificationCase(3, 4, 12, "http-basics").assignVerifier(2));

        List<VerificationCase> found = caseService().handle(new GetVerificationCasesByVerifierQuery(2));

        assertThat(found).containsExactly(second, first);
    }

    @Test
    void caseQuery_detail_listsTheFailedQuestionsWithTheChosenAnswer() {
        learningPath.addBlueprint();
        AssessmentAttempt attempt = addAttempt(List.of(1, 0, 3, 0, 2));
        VerificationCase verificationCase = cases.save(new VerificationCase(attempt.getId(), 1, 10, "http-basics"));

        Optional<VerificationCaseDetail> detail =
                caseService().handle(new GetVerificationCaseDetailQuery(verificationCase.getId()));

        assertThat(detail).isPresent();
        assertThat(detail.get().verificationCase()).isSameAs(verificationCase);
        assertThat(detail.get().attempt()).isSameAs(attempt);
        List<FailedQuestion> failed = detail.get().failedQuestions();
        assertThat(failed).extracting(FailedQuestion::position).containsExactly(2, 5);
        assertThat(failed).extracting(FailedQuestion::text).containsExactly("Question 2?", "Question 5?");
        assertThat(failed).extracting(FailedQuestion::selectedAnswer).containsExactly(0, 2);
        assertThat(failed.get(0).answers()).containsExactly("a2", "b2", "c2", "d2");
    }

    @Test
    void caseQuery_detail_whenEverythingWasCorrect_hasNoFailedQuestions() {
        learningPath.addBlueprint();
        AssessmentAttempt attempt = addAttempt(List.of(1, 2, 3, 0, 1));
        VerificationCase verificationCase = cases.save(new VerificationCase(attempt.getId(), 1, 10, "http-basics"));

        Optional<VerificationCaseDetail> detail =
                caseService().handle(new GetVerificationCaseDetailQuery(verificationCase.getId()));

        assertThat(detail.get().failedQuestions()).isEmpty();
    }

    @Test
    void caseQuery_detail_whenTheBlueprintIsGone_hasNoFailedQuestions() {
        AssessmentAttempt attempt = addAttempt(List.of(1, 0, 3, 0, 2));
        VerificationCase verificationCase = cases.save(new VerificationCase(attempt.getId(), 1, 10, "http-basics"));

        Optional<VerificationCaseDetail> detail =
                caseService().handle(new GetVerificationCaseDetailQuery(verificationCase.getId()));

        assertThat(detail).isPresent();
        assertThat(detail.get().failedQuestions()).isEmpty();
    }

    @Test
    void caseQuery_detail_forAnUnknownCase_returnsEmpty() {
        assertThat(caseService().handle(new GetVerificationCaseDetailQuery(99))).isEmpty();
    }

    @Test
    void caseQuery_detail_whenTheAttemptIsMissing_returnsEmpty() {
        VerificationCase verificationCase = cases.save(new VerificationCase(50, 1, 10, "http-basics"));

        assertThat(caseService().handle(new GetVerificationCaseDetailQuery(verificationCase.getId()))).isEmpty();
    }
}
