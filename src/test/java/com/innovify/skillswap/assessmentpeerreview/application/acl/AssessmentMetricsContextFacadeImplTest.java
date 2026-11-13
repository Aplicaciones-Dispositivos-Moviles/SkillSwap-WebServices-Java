package com.innovify.skillswap.assessmentpeerreview.application.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssessmentMetricsContextFacadeImplTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);
    private static final List<Integer> WRONG = List.of(0, 0, 0, 1, 0);
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-01T00:00:00Z");
    private static final String INSIDE = "2026-10-15T10:00:00Z";

    private final FakeAssessmentAttemptRepository attempts = new FakeAssessmentAttemptRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final AssessmentMetricsContextFacadeImpl facade =
            new AssessmentMetricsContextFacadeImpl(attempts, profiles, cases);
    private int nextBlueprint = 1;

    private void attempt(String skill, boolean passed, String weakSubTopic, String completedAt) {
        AssessmentAttempt attempt = new AssessmentAttempt(nextBlueprint++, 1, passed ? CORRECT : WRONG, CORRECT);
        if (skill != null) {
            attempt.recordAnalysis(skill, weakSubTopic);
        }
        ReflectionTestUtils.setField(attempt, "completedAt", Instant.parse(completedAt));
        attempts.save(attempt);
    }

    private void attempts(String skill, int passed, int failed) {
        for (int i = 0; i < passed; i++) {
            attempt(skill, true, null, INSIDE);
        }
        for (int i = 0; i < failed; i++) {
            attempt(skill, false, null, INSIDE);
        }
    }

    // ---------- Skill difficulty ----------

    @Test
    void getSkillDifficulty_ranksTheSkillsByFailureRate() {
        attempts("sql-fundamentals", 1, 3);
        attempts("http-basics", 3, 2);
        attempts("rest-api-design", 0, 2);
        attempts("authentication-jwt", 5, 0);
        attempt("sql-fundamentals", false, null, "2026-09-30T23:59:59Z");
        attempt("sql-fundamentals", false, null, "2026-11-01T00:00:00Z");
        attempt(null, false, null, INSIDE);

        List<SkillDifficultyView> result = facade.getSkillDifficulty(FROM, TO, 3, 10);

        assertThat(result).extracting(SkillDifficultyView::skillTag, SkillDifficultyView::attempts,
                        SkillDifficultyView::failed, SkillDifficultyView::failureRate)
                .containsExactly(tuple("sql-fundamentals", 4, 3, 0.75), tuple("http-basics", 5, 2, 0.4));
    }

    @Test
    void getSkillDifficulty_breaksTiesByAttemptsAndRespectsTheLimit() {
        attempts("a-skill", 1, 1);
        attempts("b-skill", 3, 3);

        assertThat(facade.getSkillDifficulty(FROM, TO, 1, 10)).extracting(SkillDifficultyView::skillTag)
                .containsExactly("b-skill", "a-skill");
        assertThat(facade.getSkillDifficulty(FROM, TO, 1, 1)).extracting(SkillDifficultyView::skillTag)
                .containsExactly("b-skill");
        assertThat(facade.getSkillDifficulty(FROM, TO, 1, 0)).isEmpty();
        assertThat(facade.getSkillDifficulty(TO, FROM, 1, 10)).isEmpty();
        assertThat(facade.getSkillDifficulty(null, TO, 1, 10)).isEmpty();
    }

    // ---------- Sub-topic difficulty ----------

    @Test
    void getSubTopicDifficulty_countsTheWeakestSubTopicOfTheFailedAttempts() {
        attempt("sql-fundamentals", false, "Indexes", INSIDE);
        attempt("sql-fundamentals", false, "Indexes", INSIDE);
        attempt("sql-fundamentals", false, null, INSIDE);
        attempt("sql-fundamentals", true, "Keys", INSIDE);
        attempt("http-basics", false, "Status", INSIDE);
        attempt("http-basics", false, "Headers", INSIDE);
        attempt("http-basics", false, "Headers", "2026-09-01T00:00:00Z");

        List<SubTopicDifficultyView> result = facade.getSubTopicDifficulty(FROM, TO, 10);

        assertThat(result).extracting(SubTopicDifficultyView::skillTag, SubTopicDifficultyView::subTopic,
                        SubTopicDifficultyView::weakCount, SubTopicDifficultyView::shareOfFailures)
                .containsExactly(tuple("sql-fundamentals", "Indexes", 2, 0.67),
                        tuple("http-basics", "Headers", 1, 0.5),
                        tuple("http-basics", "Status", 1, 0.5));
    }

    @Test
    void getSubTopicDifficulty_respectsTheLimitAndAnInvalidPeriod() {
        attempt("sql-fundamentals", false, "Indexes", INSIDE);
        attempt("http-basics", false, "Headers", INSIDE);

        assertThat(facade.getSubTopicDifficulty(FROM, TO, 1)).hasSize(1);
        assertThat(facade.getSubTopicDifficulty(FROM, TO, 0)).isEmpty();
        assertThat(facade.getSubTopicDifficulty(TO, FROM, 5)).isEmpty();
    }

    // ---------- Verifier activity ----------

    private void resolvedCase(int verifierId, int attemptId, long minutes) {
        VerificationCase verificationCase = cases.save(new VerificationCase(attemptId, 20, 5, "http-basics",
                CaseType.QUIZ).assignVerifier(verifierId).resolve(ReviewDecision.APPROVED, "Meets the rubric."));
        Instant assignedAt = Instant.parse("2026-10-10T08:00:00Z");
        ReflectionTestUtils.setField(verificationCase, "assignedAt", assignedAt);
        ReflectionTestUtils.setField(verificationCase, "resolvedAt", assignedAt.plusSeconds(minutes * 60));
    }

    @Test
    void getVerifierActivity_listsEveryProfileWithTheAverageResolutionTime() {
        profiles.save(new VerifierProfile(5, "http-basics"));
        profiles.save(new VerifierProfile(4, "http-basics").suspend(9, "Low reliability", Instant.now()));
        profiles.save(new VerifierProfile(3, "http-basics"));
        resolvedCase(3, 1, 120);
        resolvedCase(3, 2, 240);
        resolvedCase(4, 3, 90);
        cases.save(new VerificationCase(4, 20, 5, "http-basics", CaseType.QUIZ).assignVerifier(5));

        List<VerifierActivityView> result = facade.getVerifierActivity();

        assertThat(result).extracting(VerifierActivityView::verifierUserId, VerifierActivityView::suspended,
                        VerifierActivityView::averageResolutionHours)
                .containsExactly(tuple(3, false, 3.0), tuple(4, true, 1.5), tuple(5, false, null));
    }
}
