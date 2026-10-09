package com.innovify.skillswap.assessmentpeerreview.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseAssignmentServiceImplTest {

    private static final String SKILL = "http-basics";

    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final CaseAssignmentServiceImpl service =
            new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher());

    private void addVerifier(int userId, String skill, boolean available) {
        profiles.save(new VerifierProfile(userId, skill).setAvailability(available));
    }

    private void addVerifier(int userId) {
        addVerifier(userId, SKILL, true);
    }

    private VerificationCase addCase(int studentId, String skill) {
        return cases.save(new VerificationCase(1, studentId, 10, skill));
    }

    @Test
    void tryAssign_assignsTheCaseToAnAvailableVerifierOfTheSkill() {
        addVerifier(2);
        VerificationCase verificationCase = addCase(1, SKILL);

        boolean assigned = service.tryAssign(verificationCase);

        assertThat(assigned).isTrue();
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(verificationCase.getVerifierUserId()).isEqualTo(2);
    }

    @Test
    void tryAssign_choosesTheVerifierWithTheFewestOpenCases() {
        addVerifier(2);
        addVerifier(3);
        addCase(7, SKILL).assignVerifier(2);
        VerificationCase verificationCase = addCase(1, SKILL);

        service.tryAssign(verificationCase);

        assertThat(verificationCase.getVerifierUserId()).isEqualTo(3);
    }

    @Test
    void tryAssign_withoutQualifiedVerifiers_leavesTheCasePending() {
        addVerifier(2, "sql-fundamentals", true);
        addVerifier(3, SKILL, false);
        VerificationCase verificationCase = addCase(1, SKILL);

        boolean assigned = service.tryAssign(verificationCase);

        assertThat(assigned).isFalse();
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    @Test
    void tryAssign_neverAssignsTheCaseToItsOwnStudent() {
        addVerifier(1);
        VerificationCase verificationCase = addCase(1, SKILL);

        assertThat(service.tryAssign(verificationCase)).isFalse();
    }

    @Test
    void assignPending_assignsTheOldestCasesFirstSpreadingTheWorkload() {
        addVerifier(2);
        addVerifier(3);
        VerificationCase first = addCase(7, SKILL);
        VerificationCase second = addCase(8, SKILL);

        int assigned = service.assignPending(List.of(SKILL));

        assertThat(assigned).isEqualTo(2);
        assertThat(first.getVerifierUserId()).isEqualTo(2);
        assertThat(second.getVerifierUserId()).isEqualTo(3);
    }

    @Test
    void assignPending_onlyTouchesTheRequestedSkills() {
        addVerifier(2);
        VerificationCase other = addCase(1, "sql-fundamentals");

        int assigned = service.assignPending(List.of(SKILL));

        assertThat(assigned).isZero();
        assertThat(other.getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    @Test
    void assignPending_ignoresRepeatedSkills() {
        addVerifier(2);
        addCase(1, SKILL);

        assertThat(service.assignPending(List.of(SKILL, SKILL))).isEqualTo(1);
    }

    @Test
    void assignPending_withoutPendingCases_assignsNothing() {
        addVerifier(2);

        assertThat(service.assignPending(List.of(SKILL))).isZero();
    }

    @Test
    void tryAssign_anAppealedCase_neverGoesBackToTheVerifierWhoRejectedIt() {
        addVerifier(2);
        addVerifier(3);
        VerificationCase verificationCase = addCase(1, SKILL).assignVerifier(2)
                .resolve(ReviewDecision.REJECTED, "Needs more work.").appeal();
        // Verifier 2 has no open cases, so only the exclusion keeps them away.

        assertThat(service.tryAssign(verificationCase)).isTrue();
        assertThat(verificationCase.getVerifierUserId()).isEqualTo(3);
    }

    @Test
    void tryAssign_anAppealedCase_withOnlyTheFirstVerifierAvailable_staysPending() {
        addVerifier(2);
        VerificationCase verificationCase = addCase(1, SKILL).assignVerifier(2)
                .resolve(ReviewDecision.REJECTED, "Needs more work.").appeal();

        assertThat(service.tryAssign(verificationCase)).isFalse();
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.PENDING);
    }

    @Test
    void assignPending_givesAnAppealedCaseToANewVerifierWhenOneShowsUp() {
        addVerifier(2);
        VerificationCase verificationCase = addCase(1, SKILL).assignVerifier(2)
                .resolve(ReviewDecision.REJECTED, "Needs more work.").appeal();

        assertThat(service.assignPending(List.of(SKILL))).isZero();

        addVerifier(3);
        assertThat(service.assignPending(List.of(SKILL))).isEqualTo(1);
        assertThat(verificationCase.getVerifierUserId()).isEqualTo(3);
    }
}
