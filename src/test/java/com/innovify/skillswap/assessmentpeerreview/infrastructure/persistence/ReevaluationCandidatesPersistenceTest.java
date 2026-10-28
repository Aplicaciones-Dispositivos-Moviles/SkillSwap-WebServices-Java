package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/** The queries behind GET /api/v1/reevaluations/candidates, against PostgreSQL. */
class ReevaluationCandidatesPersistenceTest extends PostgresIntegrationTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);
    private static final List<Integer> ALL_WRONG = List.of(0, 0, 0, 3, 0);
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Autowired
    private AssessmentAttemptRepository attempts;

    @Autowired
    private VerificationCaseRepository cases;

    @Autowired
    private ReevaluationRepository reevaluations;

    private AssessmentAttempt attempt(int blueprintId, int studentId, String skill, boolean passed, Instant at) {
        AssessmentAttempt attempt = new AssessmentAttempt(blueprintId, studentId, passed ? CORRECT : ALL_WRONG,
                CORRECT).recordAnalysis(skill, null);
        ReflectionTestUtils.setField(attempt, "completedAt", at);
        return attempts.save(attempt);
    }

    private VerificationCase resolvedCase(int attemptId, int studentId, int nodeId, String skill,
                                          ReviewDecision decision, Instant at) {
        VerificationCase verificationCase = new VerificationCase(attemptId, studentId, nodeId, skill, CaseType.QUIZ)
                .assignVerifier(9).resolve(decision, "Checked against the rubric.");
        ReflectionTestUtils.setField(verificationCase, "resolvedAt", at);
        return cases.save(verificationCase);
    }

    // ---------- Attempts ----------

    @Test
    void passedAttempts_skipFailedOnesAndTheGivenStudent_newestFirst() {
        AssessmentAttempt oldest = attempt(1, 1, "http-basics", true, NOW.minusSeconds(300));
        attempt(2, 2, "http-basics", false, NOW.minusSeconds(200));
        AssessmentAttempt newest = attempt(3, 3, "http-basics", true, NOW.minusSeconds(100));
        attempt(4, 7, "http-basics", true, NOW);

        List<AssessmentAttempt> found = attempts.findRecentPassedExcludingStudent(7, null, 10);

        assertThat(found).extracting(AssessmentAttempt::getId).containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    void passedAttempts_filterBySkillAndKeepTheLimit() {
        attempt(1, 1, "http-basics", true, NOW.minusSeconds(300));
        AssessmentAttempt second = attempt(2, 2, "sql-basics", true, NOW.minusSeconds(200));
        AssessmentAttempt third = attempt(3, 3, "sql-basics", true, NOW.minusSeconds(100));

        assertThat(attempts.findRecentPassedExcludingStudent(7, "sql-basics", 10))
                .extracting(AssessmentAttempt::getId).containsExactly(third.getId(), second.getId());
        assertThat(attempts.findRecentPassedExcludingStudent(7, "sql-basics", 1))
                .extracting(AssessmentAttempt::getId).containsExactly(third.getId());
        assertThat(attempts.findRecentPassedExcludingStudent(7, "unknown-skill", 10)).isEmpty();
    }

    // ---------- Cases ----------

    @Test
    void approvedCases_skipRejectedOpenAndTheGivenStudent_mostRecentlyResolvedFirst() {
        VerificationCase oldest = resolvedCase(11, 1, 10, "http-basics", ReviewDecision.APPROVED, NOW.minusSeconds(300));
        resolvedCase(12, 2, 20, "http-basics", ReviewDecision.REJECTED, NOW.minusSeconds(200));
        cases.save(new VerificationCase(13, 3, 30, "http-basics", CaseType.QUIZ));
        VerificationCase newest = resolvedCase(14, 4, 40, "http-basics", ReviewDecision.APPROVED, NOW.minusSeconds(100));
        resolvedCase(15, 7, 70, "http-basics", ReviewDecision.APPROVED, NOW);

        List<VerificationCase> found = cases.findRecentApprovedExcludingStudent(7, null, 10);

        assertThat(found).extracting(VerificationCase::getId).containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    void approvedCases_filterBySkillAndKeepTheLimit() {
        resolvedCase(11, 1, 10, "http-basics", ReviewDecision.APPROVED, NOW.minusSeconds(300));
        VerificationCase second = resolvedCase(12, 2, 20, "sql-basics", ReviewDecision.APPROVED, NOW.minusSeconds(200));
        VerificationCase third = resolvedCase(13, 3, 30, "sql-basics", ReviewDecision.APPROVED, NOW.minusSeconds(100));

        assertThat(cases.findRecentApprovedExcludingStudent(7, "sql-basics", 10))
                .extracting(VerificationCase::getId).containsExactly(third.getId(), second.getId());
        assertThat(cases.findRecentApprovedExcludingStudent(7, "sql-basics", 1))
                .extracting(VerificationCase::getId).containsExactly(third.getId());
        assertThat(cases.findRecentApprovedExcludingStudent(7, "unknown-skill", 10)).isEmpty();
    }

    // ---------- Reevaluations ----------

    @Test
    void reevaluatedSourceIds_returnOnlyTheOnesOfThatTypeThatHaveAReevaluation() {
        reevaluations.save(new Reevaluation(ReevaluationSourceType.ATTEMPT, 5, 1, 7, "Looks copied."));
        reevaluations.save(new Reevaluation(ReevaluationSourceType.CASE, 6, 2, 7, "Doubtful."));

        assertThat(reevaluations.findReevaluatedSourceIds(ReevaluationSourceType.ATTEMPT, List.of(5, 6, 7)))
                .isEqualTo(Set.of(5));
        assertThat(reevaluations.findReevaluatedSourceIds(ReevaluationSourceType.CASE, List.of(5, 6)))
                .isEqualTo(Set.of(6));
    }

    @Test
    void reevaluatedSourceIds_withoutIds_isEmpty() {
        assertThat(reevaluations.findReevaluatedSourceIds(ReevaluationSourceType.ATTEMPT, List.of())).isEmpty();
    }
}
