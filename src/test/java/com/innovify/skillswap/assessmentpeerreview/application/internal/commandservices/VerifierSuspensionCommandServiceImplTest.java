package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReinstateVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SuspendVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionOperations;

class VerifierSuspensionCommandServiceImplTest {

    private static final int SENIOR = 7;
    private static final int WEAK = 3;
    private static final int OTHER = 4;
    private static final int STUDENT = 20;
    private static final String SKILL = "http-basics";

    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final Set<Integer> seniors = new HashSet<>();
    private final Map<Integer, Integer> reliabilities = new HashMap<>();
    private VerifierSuspensionCommandServiceImpl service;
    private int nextAttempt = 1;

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
                Integer value = reliabilities.get(userId);
                return value == null ? OptionalInt.empty() : OptionalInt.of(value);
            }

            @Override
            public Set<Integer> findSeniorVerifiers(Collection<Integer> userIds) {
                return userIds.stream().filter(seniors::contains).collect(Collectors.toSet());
            }
        };
        service = new VerifierSuspensionCommandServiceImpl(profiles, cases,
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()), reputation,
                TransactionOperations.withoutTransaction(), TestMessages.source());
        // The senior is enabled for another skill, so they never take the cases of these tests.
        profiles.save(new VerifierProfile(SENIOR, "other-skill"));
        seniors.add(SENIOR);
        profiles.save(new VerifierProfile(WEAK, SKILL));
        reliabilities.put(WEAK, 40);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private VerificationCase assignedTo(int verifierId) {
        return cases.save(new VerificationCase(nextAttempt++, STUDENT, 5, SKILL, CaseType.QUIZ,
                ReviewDeadline.hours(48)).assignVerifier(verifierId));
    }

    // ---------- Suspend ----------

    @Test
    void suspend_byASeniorAVerifierBelowTheThreshold_suspendsAndSaves() {
        var result = service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "  Many overturned decisions "));

        assertThat(result.isSuccess()).isTrue();
        VerifierProfile profile = profiles.findByUserId(WEAK).orElseThrow();
        assertThat(result.value()).isSameAs(profile);
        assertThat(profile.isSuspended()).isTrue();
        assertThat(profile.getSuspendedBy()).isEqualTo(SENIOR);
        assertThat(profile.getSuspensionReason()).isEqualTo("Many overturned decisions");
        assertThat(profile.getSuspendedAt()).isNotNull();
        assertThat(profile.canReview(SKILL)).isFalse();
    }

    @Test
    void suspend_withAReliabilityJustBelowTheThreshold_isAllowed() {
        reliabilities.put(WEAK, 59);

        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low")).isSuccess()).isTrue();
    }

    @Test
    void suspend_takesBackTheOpenCasesAndGivesThemToAnotherVerifier() {
        profiles.save(new VerifierProfile(OTHER, SKILL));
        VerificationCase first = assignedTo(WEAK);
        VerificationCase second = assignedTo(WEAK);

        var result = service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));

        assertThat(result.isSuccess()).isTrue();
        for (VerificationCase moved : new VerificationCase[] {first, second}) {
            assertThat(moved.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
            assertThat(moved.getVerifierUserId()).isEqualTo(OTHER);
            assertThat(moved.getDeadlineMissedAt()).isNull();
            assertThat(moved.getReassignmentCount()).isZero();
        }
    }

    @Test
    void suspend_withoutAnotherQualifiedVerifier_leavesTheCasesPending() {
        VerificationCase waiting = assignedTo(WEAK);

        service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));

        assertThat(waiting.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(waiting.getVerifierUserId()).isNull();
    }

    @Test
    void suspend_doesNotTouchTheResolvedCasesNorTheOnesOfOtherVerifiers() {
        profiles.save(new VerifierProfile(OTHER, SKILL));
        VerificationCase resolved = assignedTo(WEAK).resolve(ReviewDecision.APPROVED, "Meets the rubric.");
        VerificationCase ofOther = assignedTo(OTHER);

        service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));

        assertThat(resolved.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.getVerifierUserId()).isEqualTo(WEAK);
        assertThat(ofOther.getVerifierUserId()).isEqualTo(OTHER);
    }

    @Test
    void suspend_byWhoIsNotAnEnabledSenior_isForbidden() {
        profiles.save(new VerifierProfile(8, SKILL));
        seniors.add(9);

        assertThat(service.handle(new SuspendVerifierCommand(8, WEAK, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(service.handle(new SuspendVerifierCommand(9, WEAK, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        profiles.findByUserId(SENIOR).orElseThrow().revoke();
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(profiles.findByUserId(WEAK).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    void suspend_withoutOrWithATooLongJustification_isInvalid() {
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, null)).error())
                .isEqualTo(AssessmentPeerReviewError.JUSTIFICATION_REQUIRED);
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "   ")).error())
                .isEqualTo(AssessmentPeerReviewError.JUSTIFICATION_REQUIRED);
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "a".repeat(2001))).error())
                .isEqualTo(AssessmentPeerReviewError.JUSTIFICATION_TOO_LONG);
        assertThat(profiles.findByUserId(WEAK).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    void suspend_oneself_isRejected() {
        reliabilities.put(SENIOR, 95);

        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, SENIOR, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.CANNOT_SUSPEND_SELF);
    }

    @Test
    void suspend_aVerifierWithoutProfile_isNotFound() {
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, 99, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.VERIFIER_PROFILE_NOT_FOUND);
    }

    @Test
    void suspend_aSuspendedVerifier_isAConflict() {
        service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));

        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Again")).error())
                .isEqualTo(AssessmentPeerReviewError.ALREADY_SUSPENDED);
    }

    @Test
    void suspend_aVerifierNotBelowTheThresholdOrWithoutRecord_isRejected() {
        reliabilities.put(WEAK, 60);
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_LOW_RELIABILITY);

        reliabilities.remove(WEAK);
        assertThat(service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low")).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_LOW_RELIABILITY);
        assertThat(profiles.findByUserId(WEAK).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    void suspend_whenThePersistenceFails_returnsADatabaseError() {
        profiles.failOnSave(new DataAccessResourceFailureException("down"));

        var result = service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));

        assertThat(result.error()).isEqualTo(AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(result.message()).isNotBlank();
    }

    // ---------- Reinstate ----------

    @Test
    void reinstate_aSuspendedVerifier_canReviewAgainAndTakesThePendingCases() {
        service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));
        VerificationCase pending = cases.save(new VerificationCase(50, STUDENT, 5, SKILL, CaseType.QUIZ));

        var result = service.handle(new ReinstateVerifierCommand(SENIOR, WEAK));

        assertThat(result.isSuccess()).isTrue();
        VerifierProfile profile = profiles.findByUserId(WEAK).orElseThrow();
        assertThat(profile.isSuspended()).isFalse();
        assertThat(profile.getSuspensionReason()).isNull();
        assertThat(profile.canReview(SKILL)).isTrue();
        assertThat(pending.getVerifierUserId()).isEqualTo(WEAK);
        assertThat(pending.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
    }

    @Test
    void reinstate_aVerifierThatIsNotSuspended_isAConflict() {
        assertThat(service.handle(new ReinstateVerifierCommand(SENIOR, WEAK)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SUSPENDED);
    }

    @Test
    void reinstate_withoutProfile_isNotFound() {
        assertThat(service.handle(new ReinstateVerifierCommand(SENIOR, 99)).error())
                .isEqualTo(AssessmentPeerReviewError.VERIFIER_PROFILE_NOT_FOUND);
    }

    @Test
    void reinstate_byWhoIsNotASenior_isForbidden() {
        service.handle(new SuspendVerifierCommand(SENIOR, WEAK, "Low"));
        profiles.save(new VerifierProfile(8, SKILL));

        assertThat(service.handle(new ReinstateVerifierCommand(8, WEAK)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(service.handle(new ReinstateVerifierCommand(WEAK, WEAK)).error())
                .isEqualTo(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
        assertThat(profiles.findByUserId(WEAK).orElseThrow().isSuspended()).isTrue();
    }
}
