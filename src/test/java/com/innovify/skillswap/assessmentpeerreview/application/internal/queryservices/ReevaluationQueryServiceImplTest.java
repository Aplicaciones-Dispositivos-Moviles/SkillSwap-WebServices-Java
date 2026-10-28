package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeAssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReevaluationCandidate;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReevaluationCandidatesQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class ReevaluationQueryServiceImplTest {

    private static final int SENIOR_ID = 7;
    private static final int APPROVER_ID = 3;
    private static final String SKILL = "http-basics";
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private final FakeAssessmentAttemptRepository attempts = new FakeAssessmentAttemptRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeReevaluationRepository reevaluations = new FakeReevaluationRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final Set<Integer> seniors = new HashSet<>();
    private final Map<Integer, String> names = new HashMap<>();
    private ReevaluationQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        ReputationContextFacade reputation = new ReputationContextFacade() {
            @Override
            public boolean isSeniorVerifier(int userId) {
                return seniors.contains(userId);
            }

            @Override
            public OptionalInt reliabilityOf(int userId) {
                return OptionalInt.empty();
            }

            @Override
            public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
                return userIds.stream().filter(seniors::contains).collect(Collectors.toSet());
            }
        };
        IamContextFacade iam = userId -> Optional.ofNullable(names.get(userId));
        service = new ReevaluationQueryServiceImpl(attempts, cases, profiles, reevaluations, reputation, learningPath,
                iam, TestMessages.source());
        // The senior is enabled for another skill and has a blueprint of their own for the tests of their own results.
        profiles.save(new VerifierProfile(SENIOR_ID, "other-skill"));
        seniors.add(SENIOR_ID);
        learningPath.addBlueprint(1, 10, 1, SKILL, true, true);
        learningPath.addBlueprint(2, 20, 2, SKILL, true, true);
        learningPath.addBlueprint(3, 30, SENIOR_ID, SKILL, true, true);
        learningPath.addBlueprint(4, 40, 4, "other-skill", true, true);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private AssessmentAttempt attempt(int blueprintId, int studentId, String skill, boolean passed, Instant at) {
        List<Integer> selected = passed ? FakeLearningPathContextFacade.CORRECT : List.of(0, 0, 0, 3, 0);
        AssessmentAttempt attempt = attempts.save(new AssessmentAttempt(blueprintId, studentId, selected,
                FakeLearningPathContextFacade.CORRECT).recordAnalysis(skill, null));
        ReflectionTestUtils.setField(attempt, "completedAt", at);
        return attempt;
    }

    private VerificationCase approvedCase(int attemptId, int studentId, int nodeId, String skill, Instant at) {
        VerificationCase verificationCase = cases.save(new VerificationCase(attemptId, studentId, nodeId, skill,
                CaseType.QUIZ).assignVerifier(APPROVER_ID).resolve(ReviewDecision.APPROVED, "Meets the rubric."));
        ReflectionTestUtils.setField(verificationCase, "resolvedAt", at);
        return verificationCase;
    }

    private List<ReevaluationCandidate> candidates(String skillTag, Integer limit) {
        Result<List<ReevaluationCandidate>> result = service.handle(
                new GetReevaluationCandidatesQuery(SENIOR_ID, skillTag, limit));
        assertThat(result.isSuccess()).as(String.valueOf(result.message())).isTrue();
        return result.value();
    }

    // ---------- Who can ask ----------

    @Test
    void handle_byAVerifierWhoIsNotSenior_failsWithNotSeniorVerifier() {
        seniors.clear();

        Result<List<ReevaluationCandidate>> result = service.handle(
                new GetReevaluationCandidatesQuery(SENIOR_ID, null, null));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void handle_bySomeoneWithoutVerifierProfile_failsWithNotSeniorVerifier() {
        seniors.add(99);

        Result<List<ReevaluationCandidate>> result = service.handle(new GetReevaluationCandidatesQuery(99, null, null));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
    }

    // ---------- Attempts ----------

    @Test
    void handle_listsAPassedAttemptOfAnotherStudentWithoutCaseTypeNorApprover() {
        AssessmentAttempt attempt = attempt(1, 1, SKILL, true, NOW);

        List<ReevaluationCandidate> result = candidates(null, null);

        assertThat(result).hasSize(1);
        ReevaluationCandidate candidate = result.get(0);
        assertThat(candidate.sourceType()).isEqualTo(ReevaluationSourceType.ATTEMPT);
        assertThat(candidate.sourceId()).isEqualTo(attempt.getId());
        assertThat(candidate.skillTag()).isEqualTo(SKILL);
        assertThat(candidate.caseType()).isNull();
        assertThat(candidate.resolvedAt()).isEqualTo(NOW);
        assertThat(candidate.approvedByName()).isNull();
    }

    @Test
    void handle_skipsFailedAttemptsAndTheOwnOnes() {
        attempt(1, 1, SKILL, false, NOW);
        attempt(3, SENIOR_ID, SKILL, true, NOW);

        assertThat(candidates(null, null)).isEmpty();
    }

    @Test
    void handle_skipsTheAttemptsThatWereAlreadyReevaluated() {
        AssessmentAttempt reevaluated = attempt(1, 1, SKILL, true, NOW);
        AssessmentAttempt other = attempt(2, 2, SKILL, true, NOW.minusSeconds(60));
        reevaluations.save(new Reevaluation(ReevaluationSourceType.ATTEMPT, reevaluated.getId(), 1, SENIOR_ID,
                "Looks copied."));

        assertThat(candidates(null, null)).extracting(ReevaluationCandidate::sourceId).containsExactly(other.getId());
    }

    @Test
    void handle_skipsTheAttemptsWhoseNodeHasACaseInProgress() {
        attempt(1, 1, SKILL, true, NOW);
        cases.save(new VerificationCase(50, 1, 10, SKILL, CaseType.QUIZ));

        assertThat(candidates(null, null)).isEmpty();
    }

    @Test
    void handle_skipsTheAttemptsWhoseBlueprintIsUnknown() {
        attempt(99, 1, SKILL, true, NOW);

        assertThat(candidates(null, null)).isEmpty();
    }

    // ---------- Cases ----------

    @Test
    void handle_listsAnApprovedCaseWithItsTypeAndTheNameOfTheApprover() {
        names.put(APPROVER_ID, "Carla Pérez");
        VerificationCase approved = approvedCase(50, 1, 10, SKILL, NOW);

        List<ReevaluationCandidate> result = candidates(null, null);

        assertThat(result).hasSize(1);
        ReevaluationCandidate candidate = result.get(0);
        assertThat(candidate.sourceType()).isEqualTo(ReevaluationSourceType.CASE);
        assertThat(candidate.sourceId()).isEqualTo(approved.getId());
        assertThat(candidate.skillTag()).isEqualTo(SKILL);
        assertThat(candidate.caseType()).isEqualTo(CaseType.QUIZ);
        assertThat(candidate.resolvedAt()).isEqualTo(NOW);
        assertThat(candidate.approvedByName()).isEqualTo("Carla Pérez");
    }

    @Test
    void handle_withAnApproverWithoutRegisteredName_leavesTheNameEmpty() {
        approvedCase(50, 1, 10, SKILL, NOW);

        assertThat(candidates(null, null)).extracting(ReevaluationCandidate::approvedByName).containsExactly((String) null);
    }

    @Test
    void handle_skipsRejectedOpenAndOwnCases() {
        cases.save(new VerificationCase(50, 1, 10, SKILL, CaseType.QUIZ)
                .assignVerifier(APPROVER_ID).resolve(ReviewDecision.REJECTED, "Not enough."));
        cases.save(new VerificationCase(51, 2, 20, SKILL, CaseType.QUIZ));
        approvedCase(52, SENIOR_ID, 30, SKILL, NOW);

        assertThat(candidates(null, null)).isEmpty();
    }

    @Test
    void handle_aCaseReapprovedAfterAReevaluation_isACandidateAgain() {
        VerificationCase approved = approvedCase(50, 1, 10, SKILL, NOW);
        reevaluations.save(new Reevaluation(ReevaluationSourceType.CASE, approved.getId(), 1, SENIOR_ID,
                "Doubtful."));

        assertThat(candidates(null, null)).extracting(ReevaluationCandidate::sourceId)
                .containsExactly(approved.getId());
    }

    // ---------- Filters, order and limit ----------

    @Test
    void handle_filtersBySkill() {
        attempt(1, 1, SKILL, true, NOW);
        AssessmentAttempt other = attempt(4, 4, "other-skill", true, NOW);

        assertThat(candidates("other-skill", null)).extracting(ReevaluationCandidate::sourceId)
                .containsExactly(other.getId());
        assertThat(candidates("unknown-skill", null)).isEmpty();
    }

    @Test
    void handle_ordersAttemptsAndCasesNewestFirstAndKeepsTheLimit() {
        AssessmentAttempt oldest = attempt(1, 1, SKILL, true, NOW.minusSeconds(300));
        VerificationCase middle = approvedCase(50, 2, 20, SKILL, NOW.minusSeconds(200));
        AssessmentAttempt newest = attempt(4, 4, SKILL, true, NOW.minusSeconds(100));

        List<ReevaluationCandidate> all = candidates(null, null);
        assertThat(all).extracting(ReevaluationCandidate::sourceType)
                .containsExactly(ReevaluationSourceType.ATTEMPT, ReevaluationSourceType.CASE,
                        ReevaluationSourceType.ATTEMPT);
        assertThat(all).extracting(ReevaluationCandidate::sourceId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());

        assertThat(candidates(null, 2)).extracting(ReevaluationCandidate::sourceId)
                .containsExactly(newest.getId(), middle.getId());
    }
}
