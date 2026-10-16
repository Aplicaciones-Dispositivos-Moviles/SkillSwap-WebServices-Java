package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.Score;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class AssessmentPeerReviewPersistenceTest extends PostgresIntegrationTest {

    private static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);

    @Autowired
    private AssessmentAttemptRepository attempts;

    @Autowired
    private VerificationCaseRepository cases;

    @Autowired
    private VerifierProfileRepository profiles;

    @Autowired
    private ReevaluationRepository reevaluations;

    @Autowired
    private MiniProjectSubmissionRepository submissions;

    /** Saves a new case; the id is assigned to the instance that was saved. */
    private VerificationCase addCase(int attemptId, int studentId, int nodeId, String skill) {
        return cases.save(new VerificationCase(attemptId, studentId, nodeId, skill, CaseType.QUIZ));
    }

    private VerificationCase addCase(int attemptId, int studentId, int nodeId) {
        return addCase(attemptId, studentId, nodeId, "http-basics");
    }

    private VerificationCase loadCase(int caseId) {
        return cases.findById(caseId).orElseThrow();
    }

    /** Loads a case, applies a change and saves it, as the services do. */
    private void changeCase(int caseId, Consumer<VerificationCase> change) {
        VerificationCase verificationCase = loadCase(caseId);
        change.accept(verificationCase);
        cases.save(verificationCase);
    }

    private void changeProfile(int userId, Consumer<VerifierProfile> change) {
        VerifierProfile profile = profiles.findByUserId(userId).orElseThrow();
        change.accept(profile);
        profiles.save(profile);
    }

    // ---------- Attempts ----------

    @Test
    void attempt_roundTripsTheAnswersTheScoreAndTheResult() {
        attempts.save(new AssessmentAttempt(5, 7, List.of(1, 2, 3, 0, 3), CORRECT));

        AssessmentAttempt attempt = attempts.findByBlueprintId(5).orElseThrow();

        assertThat(attempt.getId()).isPositive();
        assertThat(attempt.getStudentId()).isEqualTo(7);
        assertThat(attempt.getSelectedAnswers()).containsExactly(1, 2, 3, 0, 3);
        assertThat(attempt.getScore()).isEqualTo(new Score(4, 5));
        assertThat(attempt.isPassed()).isTrue();
        assertThat(attempt.getCompletedAt()).isNotNull();
    }

    @Test
    void attempt_findById_returnsTheSavedAttempt() {
        int id = attempts.save(new AssessmentAttempt(5, 7, CORRECT, CORRECT)).getId();

        assertThat(attempts.findById(id)).isPresent();
        assertThat(attempts.findById(id + 100)).isEmpty();
    }

    @Test
    void attempt_findByBlueprintId_withAnUnknownBlueprint_returnsEmpty() {
        assertThat(attempts.findByBlueprintId(99)).isEmpty();
    }

    @Test
    void attempt_twoForTheSameBlueprint_violateTheUniqueIndex() {
        attempts.save(new AssessmentAttempt(5, 7, CORRECT, CORRECT));

        assertThatThrownBy(() -> attempts.save(new AssessmentAttempt(5, 7, List.of(0, 0, 0, 1, 0), CORRECT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Verifier profiles ----------

    @Test
    void profile_roundTripsTheSkillsTheFlagsAndTheCounters() {
        VerifierProfile profile = new VerifierProfile(3, "rest-api-design");
        profile.addSkill("http-basics");
        profile.setAvailability(false).incrementReviewCount().updateRating(4.5);
        profiles.save(profile);

        VerifierProfile loaded = profiles.findByUserId(3).orElseThrow();

        assertThat(loaded.getId()).isPositive();
        assertThat(loaded.getSkillTags()).containsExactly("http-basics", "rest-api-design");
        assertThat(loaded.isAvailable()).isFalse();
        assertThat(loaded.isVerified()).isTrue();
        assertThat(loaded.getRating()).isEqualTo(4.5);
        assertThat(loaded.getReviewCount()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void profile_addedSkillsAndChangedFlags_arePersisted() {
        profiles.save(new VerifierProfile(3, "http-basics"));

        changeProfile(3, profile -> {
            profile.addSkill("sql-fundamentals");
            profile.setAvailability(false);
            profile.incrementReviewCount();
        });

        VerifierProfile loaded = profiles.findByUserId(3).orElseThrow();
        assertThat(loaded.getSkillTags()).containsExactly("http-basics", "sql-fundamentals");
        assertThat(loaded.isAvailable()).isFalse();
        assertThat(loaded.getReviewCount()).isEqualTo(1);
    }

    @Test
    void profile_aRevokedProfile_staysRevoked() {
        profiles.save(new VerifierProfile(3, "http-basics"));

        changeProfile(3, VerifierProfile::revoke);

        VerifierProfile loaded = profiles.findByUserId(3).orElseThrow();
        assertThat(loaded.isVerified()).isFalse();
        assertThat(loaded.isAvailable()).isFalse();
    }

    @Test
    void profile_findByUserId_withAnUnknownUser_returnsEmpty() {
        assertThat(profiles.findByUserId(99)).isEmpty();
    }

    @Test
    void profile_twoForTheSameUser_violateTheUniqueIndex() {
        profiles.save(new VerifierProfile(3, "http-basics"));

        assertThatThrownBy(() -> profiles.save(new VerifierProfile(3, "sql-fundamentals")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profile_findEnabledBySkillTag_returnsTheProfilesNotRevokedWithThatSkill() {
        profiles.save(new VerifierProfile(2, "http-basics"));
        profiles.save(new VerifierProfile(3, "http-basics").setAvailability(false));
        profiles.save(new VerifierProfile(4, "http-basics").revoke());
        profiles.save(new VerifierProfile(5, "sql-fundamentals"));

        List<VerifierProfile> found = profiles.findEnabledBySkillTag("http-basics");

        assertThat(found).extracting(VerifierProfile::getVerifierUserId).containsExactlyInAnyOrder(2, 3);
    }

    // ---------- Verification cases ----------

    @Test
    void case_roundTripsEveryStageOfItsLifecycle() {
        int id = addCase(1, 1, 10).getId();

        VerificationCase pending = loadCase(id);
        assertThat(pending.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(pending.getSkillTag()).isEqualTo("http-basics");
        assertThat(pending.getCaseType()).isEqualTo(CaseType.QUIZ);
        assertThat(pending.getPathNodeId()).isEqualTo(10);
        assertThat(pending.getVerifierUserId()).isNull();
        assertThat(pending.getDecision()).isNull();
        assertThat(pending.getAssignedAt()).isNull();
        assertThat(pending.getOpenedAt()).isNotNull();

        changeCase(id, c -> c.assignVerifier(2).attachEvidence("https://github.com/student/app"));
        VerificationCase assigned = loadCase(id);
        assertThat(assigned.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(assigned.getVerifierUserId()).isEqualTo(2);
        assertThat(assigned.getEvidenceUrl()).isEqualTo("https://github.com/student/app");
        assertThat(assigned.getAssignedAt()).isNotNull();

        changeCase(id, c -> c.resolve(ReviewDecision.APPROVED, "Meets the rubric."));
        VerificationCase resolved = loadCase(id);
        assertThat(resolved.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(resolved.getRubricNotes()).isEqualTo("Meets the rubric.");
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void case_storesTheStatusAndTheDecisionAsTheTextOfTheCSharpApi() throws Exception {
        int id = addCase(1, 1, 10).getId();
        assertThat(queryString("SELECT status FROM verification_cases WHERE id = " + id)).isEqualTo("Pending");

        changeCase(id, c -> c.assignVerifier(2).resolve(ReviewDecision.REJECTED, "Needs work."));

        assertThat(queryString("SELECT status FROM verification_cases WHERE id = " + id)).isEqualTo("Resolved");
        assertThat(queryString("SELECT decision FROM verification_cases WHERE id = " + id)).isEqualTo("Rejected");
    }

    @Test
    void case_storesTheTypeAsQuizOrMiniProject() throws Exception {
        int quiz = addCase(1, 1, 10).getId();
        int miniProject = cases.save(new VerificationCase(2, 1, 11, "http-basics", CaseType.MINI_PROJECT)).getId();

        assertThat(queryString("SELECT case_type FROM verification_cases WHERE id = " + quiz)).isEqualTo("Quiz");
        assertThat(queryString("SELECT case_type FROM verification_cases WHERE id = " + miniProject))
                .isEqualTo("MiniProject");
        assertThat(loadCase(miniProject).getCaseType()).isEqualTo(CaseType.MINI_PROJECT);
    }

    @Test
    void case_withoutTypeOrWithAnUnknownOne_isRejectedByTheDatabase() {
        String insert = "INSERT INTO verification_cases (attempt_id, student_id, path_node_id, skill_tag, status, "
                + "opened_at%s) VALUES (1, 1, 10, 'http-basics', 'Pending', now()%s)";

        assertThatThrownBy(() -> execute(insert.formatted("", ""))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(insert.formatted(", case_type", ", 'Essay'")))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void attempt_storesTheScoreAndTheAnswersAsTheCSharpApiDid() throws Exception {
        int id = attempts.save(new AssessmentAttempt(5, 7, List.of(1, 2, 3, 0, 3), CORRECT)).getId();

        assertThat(queryString("SELECT score FROM assessment_attempts WHERE id = " + id)).isEqualTo("4/5");
        assertThat(queryString("SELECT selected_answers::text FROM assessment_attempts WHERE id = " + id))
                .isEqualTo("[1, 2, 3, 0, 3]");
    }

    @Test
    void case_findByVerifierUserId_returnsTheirCasesNewestFirst() {
        int first = addCase(1, 1, 10).getId();
        int other = addCase(2, 4, 11).getId();
        int second = addCase(3, 5, 12).getId();
        changeCase(first, c -> c.assignVerifier(2));
        changeCase(other, c -> c.assignVerifier(3));
        changeCase(second, c -> c.assignVerifier(2));

        List<VerificationCase> found = cases.findByVerifierUserId(2);

        assertThat(found).extracting(VerificationCase::getId).containsExactly(second, first);
    }

    @Test
    void case_findOpenByStudentAndNode_ignoresResolvedCases() {
        int id = addCase(1, 1, 10).getId();

        assertThat(cases.findOpenByStudentAndNode(1, 10)).hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(id));
        assertThat(cases.findOpenByStudentAndNode(1, 11)).isEmpty();

        changeCase(id, c -> c.assignVerifier(2).resolve(ReviewDecision.REJECTED, "Needs work."));

        assertThat(cases.findOpenByStudentAndNode(1, 10)).isEmpty();
    }

    @Test
    void case_twoOpenCasesForTheSameStudentAndNode_violateTheUniqueIndex() {
        addCase(1, 1, 10);

        assertThatThrownBy(() -> addCase(2, 1, 10)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void case_aNewOneIsAllowedOnceThePreviousWasResolved() {
        int first = addCase(1, 1, 10).getId();
        changeCase(first, c -> c.assignVerifier(2).resolve(ReviewDecision.REJECTED, "Needs work."));

        int second = addCase(2, 1, 10).getId();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void case_twoForTheSameAttempt_violateTheUniqueIndex() {
        addCase(1, 1, 10);

        assertThatThrownBy(() -> addCase(1, 4, 11)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void case_findPendingBySkillTag_returnsOnlyPendingCasesOfTheSkillOldestFirst() {
        int first = addCase(1, 1, 10).getId();
        int second = addCase(2, 4, 11).getId();
        addCase(3, 5, 12, "sql-fundamentals");
        int assigned = addCase(4, 6, 13).getId();
        changeCase(assigned, c -> c.assignVerifier(2));

        List<VerificationCase> found = cases.findPendingBySkillTag("http-basics");

        assertThat(found).extracting(VerificationCase::getId).containsExactly(first, second);
    }

    @Test
    void case_countOpenByVerifierUserIds_countsOnlyUnresolvedCases() {
        int one = addCase(1, 1, 10).getId();
        int two = addCase(2, 4, 11).getId();
        int done = addCase(3, 5, 12).getId();
        changeCase(one, c -> c.assignVerifier(2));
        changeCase(two, c -> c.assignVerifier(2));
        changeCase(done, c -> c.assignVerifier(2).resolve(ReviewDecision.APPROVED, "Good."));

        Map<Integer, Integer> counts = cases.countOpenByVerifierUserIds(new ArrayList<>(List.of(2, 3)));

        assertThat(counts.get(2)).isEqualTo(2);
        assertThat(counts.getOrDefault(3, 0)).isZero();
    }

    @Test
    void case_countOpenByVerifierUserIds_withoutUsers_isEmpty() {
        assertThat(cases.countOpenByVerifierUserIds(List.of())).isEmpty();
    }

    @Test
    void case_anAppeal_roundTripsTheCountAndThePreviousVerifier() {
        int id = addCase(1, 1, 10).getId();
        changeCase(id, c -> c.assignVerifier(2).attachEvidence("https://github.com/student/app")
                .resolve(ReviewDecision.REJECTED, "Needs work."));

        changeCase(id, VerificationCase::appeal);

        VerificationCase appealed = loadCase(id);
        assertThat(appealed.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(appealed.getVerifierUserId()).isNull();
        assertThat(appealed.getDecision()).isNull();
        assertThat(appealed.getRubricNotes()).isNull();
        assertThat(appealed.getResolvedAt()).isNull();
        assertThat(appealed.getAppealCount()).isEqualTo(1);
        assertThat(appealed.getPreviousVerifierUserId()).isEqualTo(2);
        assertThat(appealed.getEvidenceUrl()).isEqualTo("https://github.com/student/app");
    }

    @Test
    void case_aRowWithoutAppeals_isReadWithZeroAppealsAndNoPreviousVerifier() {
        int id = addCase(1, 1, 10).getId();

        VerificationCase stored = loadCase(id);

        assertThat(stored.getAppealCount()).isZero();
        assertThat(stored.getPreviousVerifierUserId()).isNull();
    }

    @Test
    void case_anAppealedCaseCountsAsOpenAndIsFoundAsPending() {
        int id = addCase(1, 1, 10).getId();
        changeCase(id, c -> c.assignVerifier(2).resolve(ReviewDecision.REJECTED, "Needs work."));
        changeCase(id, VerificationCase::appeal);

        assertThat(cases.findOpenByStudentAndNode(1, 10)).isPresent();
        assertThat(cases.findPendingBySkillTag("http-basics")).extracting(VerificationCase::getId)
                .containsExactly(id);
        assertThat(cases.countOpenByVerifierUserIds(List.of(2))).isEmpty();
    }

    // ---------- Reevaluations ----------

    @Test
    void reevaluation_roundTripsTheSourceTheSeniorAndTheJustification() {
        reevaluations.save(new Reevaluation(ReevaluationSourceType.CASE, 4, 7, 9, "The rubric was not applied."));

        List<Reevaluation> found = reevaluations.findBySource(ReevaluationSourceType.CASE, 4);

        assertThat(found).hasSize(1);
        Reevaluation reevaluation = found.get(0);
        assertThat(reevaluation.getId()).isNotNull();
        assertThat(reevaluation.getSourceType()).isEqualTo(ReevaluationSourceType.CASE);
        assertThat(reevaluation.getSourceId()).isEqualTo(4);
        assertThat(reevaluation.getStudentId()).isEqualTo(7);
        assertThat(reevaluation.getOrderedByUserId()).isEqualTo(9);
        assertThat(reevaluation.getJustification()).isEqualTo("The rubric was not applied.");
        assertThat(reevaluation.getCreatedAt()).isNotNull();
        assertThat(reevaluations.findBySource(ReevaluationSourceType.ATTEMPT, 4)).isEmpty();
    }

    @Test
    void reevaluation_ofTheSameSourceTwice_areListedFromTheOldestToTheNewest() {
        reevaluations.save(new Reevaluation(ReevaluationSourceType.ATTEMPT, 5, 7, 9, "First doubt."));
        reevaluations.save(new Reevaluation(ReevaluationSourceType.ATTEMPT, 5, 7, 8, "Second doubt."));

        assertThat(reevaluations.findBySource(ReevaluationSourceType.ATTEMPT, 5))
                .extracting(Reevaluation::getJustification).containsExactly("First doubt.", "Second doubt.");
    }

    @Test
    void case_reopenedForReevaluation_keepsTheCountAndForgetsTheDecision() {
        VerificationCase created = addCase(1, 7, 10);
        changeCase(created.getId(), c -> c.assignVerifier(2).resolve(ReviewDecision.APPROVED, "Good."));
        changeCase(created.getId(), c -> c.reopenForReevaluation(ReviewDeadline.hours(48)));

        VerificationCase loaded = loadCase(created.getId());

        assertThat(loaded.getReevaluationCount()).isEqualTo(1);
        assertThat(loaded.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(loaded.getDecision()).isNull();
        assertThat(loaded.getVerifierUserId()).isNull();
        assertThat(loaded.getPreviousVerifierUserId()).isEqualTo(2);
        assertThat(loaded.getReviewDueAt()).isNotNull();
    }

    @Test
    void case_neverReevaluated_hasACountOfZero() {
        VerificationCase created = addCase(1, 7, 10);

        assertThat(loadCase(created.getId()).getReevaluationCount()).isZero();
    }

    // ---------- Mini-project submissions ----------

    @Test
    void submission_roundTripsTheMiniProjectTheStudentAndTheRepository() {
        submissions.save(new MiniProjectSubmission(3, 7, 10, "http-basics", "https://github.com/ana/repo"));

        MiniProjectSubmission loaded = submissions.findByMiniProjectId(3).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getMiniProjectId()).isEqualTo(3);
        assertThat(loaded.getStudentId()).isEqualTo(7);
        assertThat(loaded.getPathNodeId()).isEqualTo(10);
        assertThat(loaded.getSkillTag()).isEqualTo("http-basics");
        assertThat(loaded.getRepositoryUrl()).isEqualTo("https://github.com/ana/repo");
        assertThat(loaded.getSubmittedAt()).isNotNull();
        assertThat(submissions.findById(loaded.getId())).isPresent();
        assertThat(submissions.findByMiniProjectId(4)).isEmpty();
    }

    @Test
    void submission_ofTheSameMiniProjectTwice_isRejectedByTheDatabase() {
        submissions.save(new MiniProjectSubmission(3, 7, 10, "http-basics", "https://github.com/ana/repo"));

        assertThatThrownBy(() -> submissions.save(
                new MiniProjectSubmission(3, 7, 10, "http-basics", "https://github.com/ana/other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
