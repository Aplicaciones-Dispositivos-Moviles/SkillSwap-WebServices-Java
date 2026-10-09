package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationCaseTest {

    private static final int STUDENT_ID = 1;
    private static final int VERIFIER_ID = 2;

    private static VerificationCase newCase() {
        return new VerificationCase(10, STUDENT_ID, 5, " http-basics ");
    }

    private static VerificationCase assignedCase() {
        return newCase().assignVerifier(VERIFIER_ID);
    }

    // ---------- Constructor ----------

    @Test
    void constructor_opensAPendingCaseWithoutVerifier() {
        Instant before = Instant.now();

        VerificationCase verificationCase = newCase();

        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(verificationCase.getAttemptId()).isEqualTo(10);
        assertThat(verificationCase.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(verificationCase.getPathNodeId()).isEqualTo(5);
        assertThat(verificationCase.getSkillTag()).isEqualTo("http-basics");
        assertThat(verificationCase.getVerifierUserId()).isNull();
        assertThat(verificationCase.getDecision()).isNull();
        assertThat(verificationCase.isOpen()).isTrue();
        assertThat(verificationCase.getOpenedAt()).isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @CsvSource({"0,1,1,skill", "1,0,1,skill", "1,1,0,skill", "1,1,1,' '"})
    void constructor_withInvalidData_throwsDomainException(int attemptId, int studentId, int pathNodeId,
                                                           String skillTag) {
        assertThatThrownBy(() -> new VerificationCase(attemptId, studentId, pathNodeId, skillTag))
                .isInstanceOf(DomainException.class);
    }

    // ---------- Assign ----------

    @Test
    void assignVerifier_movesTheCaseToAssigned() {
        VerificationCase verificationCase = assignedCase();

        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(verificationCase.getVerifierUserId()).isEqualTo(VERIFIER_ID);
        assertThat(verificationCase.getAssignedAt()).isNotNull();
        assertThat(verificationCase.isAssignedTo(VERIFIER_ID)).isTrue();
        assertThat(verificationCase.isAssignedTo(99)).isFalse();
    }

    @Test
    void isAssignedTo_isFalseWhileTheCaseIsPending() {
        assertThat(newCase().isAssignedTo(VERIFIER_ID)).isFalse();
    }

    @Test
    void assignVerifier_toTheStudentOfTheCase_throwsDomainException() {
        assertThatThrownBy(() -> newCase().assignVerifier(STUDENT_ID)).isInstanceOf(DomainException.class);
    }

    @Test
    void assignVerifier_withAnInvalidVerifier_throwsDomainException() {
        assertThatThrownBy(() -> newCase().assignVerifier(0)).isInstanceOf(DomainException.class);
    }

    @Test
    void assignVerifier_toACaseAlreadyAssigned_throwsDomainException() {
        assertThatThrownBy(() -> assignedCase().assignVerifier(3)).isInstanceOf(DomainException.class);
    }

    // ---------- Evidence ----------

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/student/project", "http://portfolio.example.com/work"})
    void attachEvidence_withAValidLink_keepsItTrimmed(String url) {
        VerificationCase verificationCase = newCase().attachEvidence("  " + url + "  ");

        assertThat(verificationCase.getEvidenceUrl()).isEqualTo(url);
    }

    @Test
    void attachEvidence_toAnAssignedCase_isAllowedAndReplacesThePreviousLink() {
        VerificationCase verificationCase = assignedCase()
                .attachEvidence("https://github.com/student/one")
                .attachEvidence("https://github.com/student/two");

        assertThat(verificationCase.getEvidenceUrl()).isEqualTo("https://github.com/student/two");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "ftp://files.example.com/work", "javascript:alert(1)",
            "https://", "/relative/path"})
    void attachEvidence_withAnInvalidLink_throwsDomainException(String url) {
        assertThatThrownBy(() -> newCase().attachEvidence(url)).isInstanceOf(DomainException.class);
    }

    @Test
    void attachEvidence_withNull_throwsDomainException() {
        assertThatThrownBy(() -> newCase().attachEvidence(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void attachEvidence_withATooLongLink_throwsDomainException() {
        String url = "https://example.com/" + "a".repeat(VerificationCase.MAX_EVIDENCE_URL_LENGTH);

        assertThatThrownBy(() -> newCase().attachEvidence(url)).isInstanceOf(DomainException.class);
    }

    @Test
    void attachEvidence_toAResolvedCase_throwsDomainException() {
        VerificationCase verificationCase = assignedCase().resolve(ReviewDecision.REJECTED, "Needs work.");

        assertThatThrownBy(() -> verificationCase.attachEvidence("https://github.com/student/project"))
                .isInstanceOf(DomainException.class);
    }

    // ---------- Resolve ----------

    @ParameterizedTest
    @EnumSource(ReviewDecision.class)
    void resolve_recordsTheDecisionAndTheNotes(ReviewDecision decision) {
        Instant before = Instant.now();

        VerificationCase verificationCase = assignedCase().resolve(decision, "  Good use of status codes.  ");

        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(verificationCase.getDecision()).isEqualTo(decision);
        assertThat(verificationCase.getRubricNotes()).isEqualTo("Good use of status codes.");
        assertThat(verificationCase.getResolvedAt()).isBetween(before, Instant.now());
        assertThat(verificationCase.isOpen()).isFalse();
    }

    @Test
    void resolve_aPendingCase_throwsDomainException() {
        assertThatThrownBy(() -> newCase().resolve(ReviewDecision.APPROVED, "Notes"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_aResolvedCase_throwsDomainException() {
        VerificationCase verificationCase = assignedCase().resolve(ReviewDecision.APPROVED, "Notes");

        assertThatThrownBy(() -> verificationCase.resolve(ReviewDecision.REJECTED, "Other"))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void resolve_withoutNotes_throwsDomainException(String notes) {
        assertThatThrownBy(() -> assignedCase().resolve(ReviewDecision.APPROVED, notes))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_withTooLongNotes_throwsDomainException() {
        String notes = "a".repeat(VerificationCase.MAX_RUBRIC_NOTES_LENGTH + 1);

        assertThatThrownBy(() -> assignedCase().resolve(ReviewDecision.APPROVED, notes))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_withoutADecision_throwsDomainException() {
        assertThatThrownBy(() -> assignedCase().resolve(null, "Notes")).isInstanceOf(DomainException.class);
    }

    // ---------- Appeal ----------

    private static VerificationCase rejectedCase() {
        return assignedCase().resolve(ReviewDecision.REJECTED, "Needs more work.");
    }

    @Test
    void canBeAppealed_onlyForARejectedCaseWithAppealsLeft() {
        assertThat(newCase().canBeAppealed()).isFalse();
        assertThat(assignedCase().canBeAppealed()).isFalse();
        assertThat(assignedCase().resolve(ReviewDecision.APPROVED, "Good.").canBeAppealed()).isFalse();
        assertThat(rejectedCase().canBeAppealed()).isTrue();
    }

    @Test
    void appeal_reopensTheCaseWithoutVerifierNorDecision() {
        VerificationCase verificationCase = assignedCase();
        verificationCase.attachEvidence("https://github.com/student/project");
        verificationCase.resolve(ReviewDecision.REJECTED, "Needs more work.");

        verificationCase.appeal();

        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(verificationCase.getVerifierUserId()).isNull();
        assertThat(verificationCase.getDecision()).isNull();
        assertThat(verificationCase.getRubricNotes()).isNull();
        assertThat(verificationCase.getAssignedAt()).isNull();
        assertThat(verificationCase.getResolvedAt()).isNull();
        assertThat(verificationCase.getAppealCount()).isEqualTo(1);
        assertThat(verificationCase.getPreviousVerifierUserId()).isEqualTo(VERIFIER_ID);
        assertThat(verificationCase.getEvidenceUrl()).isEqualTo("https://github.com/student/project");
        assertThat(verificationCase.isOpen()).isTrue();
    }

    @Test
    void appeal_aCaseThatIsNotRejected_throwsDomainException() {
        assertThatThrownBy(() -> newCase().appeal()).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> assignedCase().appeal()).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> assignedCase().resolve(ReviewDecision.APPROVED, "Good.").appeal())
                .isInstanceOf(DomainException.class);
    }

    @Test
    void appeal_twice_throwsDomainException() {
        VerificationCase verificationCase = rejectedCase().appeal().assignVerifier(3)
                .resolve(ReviewDecision.REJECTED, "Still not enough.");

        assertThat(verificationCase.canBeAppealed()).isFalse();
        assertThatThrownBy(verificationCase::appeal).isInstanceOf(DomainException.class);
    }

    @Test
    void assignVerifier_afterAnAppeal_toTheFirstVerifier_throwsDomainException() {
        VerificationCase verificationCase = rejectedCase().appeal();

        assertThatThrownBy(() -> verificationCase.assignVerifier(VERIFIER_ID))
                .isInstanceOf(DomainException.class);
        assertThat(verificationCase.assignVerifier(3).getVerifierUserId()).isEqualTo(3);
    }
}
