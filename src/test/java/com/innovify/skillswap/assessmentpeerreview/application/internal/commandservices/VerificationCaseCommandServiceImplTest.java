package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeLearningPathContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerifierProfileRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentServiceImpl;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AppealVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.services.DefaultVerifierMatcher;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

class VerificationCaseCommandServiceImplTest {

    private static final int STUDENT_ID = 1;
    private static final int VERIFIER_ID = 2;
    private static final String EVIDENCE_URL = "https://github.com/student/project";

    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final FakeVerifierProfileRepository profiles = new FakeVerifierProfileRepository();
    private final FakeLearningPathContextFacade learningPath = new FakeLearningPathContextFacade();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private VerificationCaseCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new VerificationCaseCommandServiceImpl(cases, profiles,
                new CaseAssignmentServiceImpl(profiles, cases, new DefaultVerifierMatcher()), learningPath, events,
                TransactionOperations.withoutTransaction(), TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private VerificationCase addAssignedCase() {
        return cases.save(new VerificationCase(1, STUDENT_ID, 10, "http-basics").assignVerifier(VERIFIER_ID));
    }

    private VerifierProfile addVerifier(int userId) {
        return profiles.save(new VerifierProfile(userId, "http-basics"));
    }

    private Result<VerificationCase> attach(int caseId, int studentId, String url) {
        return service.handle(new AttachCaseEvidenceCommand(caseId, studentId, url));
    }

    private Result<VerificationCase> attach(int caseId) {
        return attach(caseId, STUDENT_ID, EVIDENCE_URL);
    }

    private Result<VerificationCase> resolve(int caseId, int verifierId, ReviewDecision decision, String notes) {
        return service.handle(new ResolveVerificationCaseCommand(caseId, verifierId, decision, notes));
    }

    private Result<VerificationCase> resolve(int caseId) {
        return resolve(caseId, VERIFIER_ID, ReviewDecision.APPROVED, "Meets the rubric.");
    }

    private static void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank();
    }

    // ---------- Attach evidence ----------

    @Test
    void attach_byTheOwner_storesTheEvidence() {
        VerificationCase verificationCase = addAssignedCase();

        Result<VerificationCase> result = attach(verificationCase.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getEvidenceUrl()).isEqualTo(EVIDENCE_URL);
    }

    @Test
    void attach_toAPendingCase_isAllowed() {
        VerificationCase pending = cases.save(new VerificationCase(1, STUDENT_ID, 10, "http-basics"));

        assertThat(attach(pending.getId()).isSuccess()).isTrue();
    }

    @Test
    void attach_toAnUnknownCase_failsWithCaseNotFound() {
        assertFailure(attach(99), AssessmentPeerReviewError.CASE_NOT_FOUND);
    }

    @Test
    void attach_byAnotherStudent_failsWithNotCaseOwner() {
        VerificationCase verificationCase = addAssignedCase();

        assertFailure(attach(verificationCase.getId(), 7, EVIDENCE_URL), AssessmentPeerReviewError.NOT_CASE_OWNER);
        assertThat(verificationCase.getEvidenceUrl()).isNull();
    }

    @Test
    void attach_toAResolvedCase_failsWithCaseAlreadyResolved() {
        VerificationCase verificationCase = addAssignedCase();
        verificationCase.resolve(ReviewDecision.REJECTED, "Needs work.");

        assertFailure(attach(verificationCase.getId()), AssessmentPeerReviewError.CASE_ALREADY_RESOLVED);
    }

    @Test
    void attach_withAnInvalidLink_failsWithInvalidEvidenceUrl() {
        VerificationCase verificationCase = addAssignedCase();

        assertFailure(attach(verificationCase.getId(), STUDENT_ID, "not a link"),
                AssessmentPeerReviewError.INVALID_EVIDENCE_URL);
        assertThat(verificationCase.getEvidenceUrl()).isNull();
    }

    @Test
    void attach_whenPersistenceFails_failsWithDatabaseError() {
        VerificationCase verificationCase = addAssignedCase();
        cases.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(attach(verificationCase.getId()), AssessmentPeerReviewError.DATABASE_ERROR);
    }

    // ---------- Resolve ----------

    @Test
    void resolve_asApproved_resolvesTheCaseCompletesTheNodeAndPublishesTheEvent() {
        VerificationCase verificationCase = addAssignedCase();
        VerifierProfile profile = addVerifier(VERIFIER_ID);

        Result<VerificationCase> result = resolve(verificationCase.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(result.value().getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(result.value().getRubricNotes()).isEqualTo("Meets the rubric.");
        assertThat(learningPath.completedNodes()).containsExactly(10);
        assertThat(profile.getReviewCount()).isEqualTo(1);
        assertThat(events.published()).hasSize(1);
        var published = (VerificationCaseResolved) events.published().get(0);
        assertThat(published.caseId()).isEqualTo(verificationCase.getId());
        assertThat(published.studentId()).isEqualTo(STUDENT_ID);
        assertThat(published.verifierUserId()).isEqualTo(VERIFIER_ID);
        assertThat(published.pathNodeId()).isEqualTo(10);
        assertThat(published.skillTag()).isEqualTo("http-basics");
        assertThat(published.decision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(published.overturnedVerifierUserId()).isNull();
    }

    @Test
    void resolve_asRejected_doesNotCompleteTheNodeButStillPublishesTheEvent() {
        VerificationCase verificationCase = addAssignedCase();
        VerifierProfile profile = addVerifier(VERIFIER_ID);

        Result<VerificationCase> result = resolve(verificationCase.getId(), VERIFIER_ID, ReviewDecision.REJECTED,
                "Missing tests.");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getDecision()).isEqualTo(ReviewDecision.REJECTED);
        assertThat(learningPath.completedNodes()).isEmpty();
        assertThat(profile.getReviewCount()).isEqualTo(1);
        assertThat(((VerificationCaseResolved) events.published().get(0)).decision())
                .isEqualTo(ReviewDecision.REJECTED);
    }

    @Test
    void resolve_anApprovedCaseWhoseNodeIsAlreadyCompleted_stillSucceeds() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        learningPath.setNextOutcome(NodeCompletionOutcome.ALREADY_COMPLETED);

        assertThat(resolve(verificationCase.getId()).isSuccess()).isTrue();
    }

    @Test
    void resolve_anUnknownCase_failsWithCaseNotFound() {
        addVerifier(VERIFIER_ID);

        assertFailure(resolve(99), AssessmentPeerReviewError.CASE_NOT_FOUND);
    }

    @Test
    void resolve_byAVerifierWhoIsNotAssigned_failsWithNotAssignedVerifier() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(3);

        assertFailure(resolve(verificationCase.getId(), 3, ReviewDecision.APPROVED, "Meets the rubric."),
                AssessmentPeerReviewError.NOT_ASSIGNED_VERIFIER);
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
    }

    @Test
    void resolve_aPendingCase_failsWithNotAssignedVerifier() {
        VerificationCase pending = cases.save(new VerificationCase(1, STUDENT_ID, 10, "http-basics"));
        addVerifier(VERIFIER_ID);

        assertFailure(resolve(pending.getId()), AssessmentPeerReviewError.NOT_ASSIGNED_VERIFIER);
    }

    @Test
    void resolve_aResolvedCase_failsWithCaseAlreadyResolved() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        resolve(verificationCase.getId());

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.CASE_ALREADY_RESOLVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void resolve_withoutNotes_failsWithRubricNotesRequired(String notes) {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);

        assertFailure(resolve(verificationCase.getId(), VERIFIER_ID, ReviewDecision.APPROVED, notes),
                AssessmentPeerReviewError.RUBRIC_NOTES_REQUIRED);
        assertThat(verificationCase.getStatus()).isEqualTo(CaseStatus.ASSIGNED);
    }

    @Test
    void resolve_withNullNotes_failsWithRubricNotesRequired() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);

        assertFailure(resolve(verificationCase.getId(), VERIFIER_ID, ReviewDecision.APPROVED, null),
                AssessmentPeerReviewError.RUBRIC_NOTES_REQUIRED);
    }

    @Test
    void resolve_withTooLongNotes_failsWithRubricNotesTooLong() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        String notes = "a".repeat(VerificationCase.MAX_RUBRIC_NOTES_LENGTH + 1);

        assertFailure(resolve(verificationCase.getId(), VERIFIER_ID, ReviewDecision.APPROVED, notes),
                AssessmentPeerReviewError.RUBRIC_NOTES_TOO_LONG);
    }

    @Test
    void resolve_withoutADecision_failsWithInvalidDecision() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);

        assertFailure(resolve(verificationCase.getId(), VERIFIER_ID, null, "Meets the rubric."),
                AssessmentPeerReviewError.INVALID_DECISION);
    }

    @Test
    void resolve_withoutAVerifierProfile_failsWithNotAVerifier() {
        VerificationCase verificationCase = addAssignedCase();

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.NOT_A_VERIFIER);
    }

    @Test
    void resolve_withARevokedProfile_failsWithNotAVerifier() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID).revoke();

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.NOT_A_VERIFIER);
    }

    @Test
    void resolve_whenTheNodeCannotBeCompleted_failsAndPublishesNothing() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        learningPath.setNextOutcome(NodeCompletionOutcome.NODE_NOT_FOUND);

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void resolve_whenCompletingTheNodeFails_failsWithDatabaseError() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        learningPath.setNextOutcome(NodeCompletionOutcome.FAILED);

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void resolve_whenPersistenceFails_failsWithDatabaseError() {
        VerificationCase verificationCase = addAssignedCase();
        addVerifier(VERIFIER_ID);
        cases.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(resolve(verificationCase.getId()), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    // ---------- Appeal ----------

    private VerificationCase addRejectedCase() {
        return cases.save(new VerificationCase(1, STUDENT_ID, 10, "http-basics").assignVerifier(VERIFIER_ID)
                .resolve(ReviewDecision.REJECTED, "Needs more work."));
    }

    private Result<VerificationCase> appeal(int caseId, int studentId) {
        return service.handle(new AppealVerificationCaseCommand(caseId, studentId));
    }

    @Test
    void appeal_assignsTheCaseToAnotherVerifier() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(VERIFIER_ID);
        addVerifier(3);

        Result<VerificationCase> result = appeal(verificationCase.getId(), STUDENT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(CaseStatus.ASSIGNED);
        assertThat(result.value().getVerifierUserId()).isEqualTo(3);
        assertThat(result.value().getPreviousVerifierUserId()).isEqualTo(VERIFIER_ID);
        assertThat(result.value().getAppealCount()).isEqualTo(1);
        assertThat(result.value().getDecision()).isNull();
    }

    @Test
    void appeal_withNobodyElseAvailable_leavesTheCasePending() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(VERIFIER_ID);

        Result<VerificationCase> result = appeal(verificationCase.getId(), STUDENT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(result.value().getVerifierUserId()).isNull();
    }

    @Test
    void appeal_aMissingCase_failsWithCaseNotFound() {
        assertFailure(appeal(99, STUDENT_ID), AssessmentPeerReviewError.CASE_NOT_FOUND);
    }

    @Test
    void appeal_byAnotherUser_failsWithNotCaseOwner() {
        VerificationCase verificationCase = addRejectedCase();

        assertFailure(appeal(verificationCase.getId(), 77), AssessmentPeerReviewError.NOT_CASE_OWNER);
        assertFailure(appeal(verificationCase.getId(), VERIFIER_ID), AssessmentPeerReviewError.NOT_CASE_OWNER);
    }

    @Test
    void appeal_anOpenCase_failsWithCaseNotAppealable() {
        VerificationCase verificationCase = addAssignedCase();

        assertFailure(appeal(verificationCase.getId(), STUDENT_ID), AssessmentPeerReviewError.CASE_NOT_APPEALABLE);
    }

    @Test
    void appeal_anApprovedCase_failsWithCaseNotAppealable() {
        VerificationCase verificationCase = addAssignedCase().resolve(ReviewDecision.APPROVED, "Good.");

        assertFailure(appeal(verificationCase.getId(), STUDENT_ID), AssessmentPeerReviewError.CASE_NOT_APPEALABLE);
    }

    @Test
    void appeal_twice_failsWithAppealAlreadyUsed() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(3);
        appeal(verificationCase.getId(), STUDENT_ID);
        verificationCase.resolve(ReviewDecision.REJECTED, "Still not enough.");

        assertFailure(appeal(verificationCase.getId(), STUDENT_ID), AssessmentPeerReviewError.APPEAL_ALREADY_USED);
    }

    @Test
    void appeal_whenTheStudentHasAnotherOpenCaseForTheNode_failsWithOpenCaseAlreadyExists() {
        VerificationCase rejected = addRejectedCase();
        cases.save(new VerificationCase(2, STUDENT_ID, 10, "http-basics"));

        assertFailure(appeal(rejected.getId(), STUDENT_ID), AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
        assertThat(rejected.getStatus()).isEqualTo(CaseStatus.RESOLVED);
    }

    @Test
    void appeal_whenPersistenceFails_failsWithDatabaseError() {
        VerificationCase verificationCase = addRejectedCase();
        cases.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(appeal(verificationCase.getId(), STUDENT_ID), AssessmentPeerReviewError.DATABASE_ERROR);
    }

    @Test
    void appeal_thenTheSecondVerifierResolves_finalDecisionIsRecorded() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(VERIFIER_ID);
        addVerifier(3);
        appeal(verificationCase.getId(), STUDENT_ID);

        Result<VerificationCase> result = resolve(verificationCase.getId(), 3, ReviewDecision.APPROVED, "Now it is fine.");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(learningPath.completedNodes()).contains(10);
    }

    @Test
    void resolve_anAppealedCaseAsApproved_publishesTheOverturnedVerifier() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(VERIFIER_ID);
        addVerifier(3);
        appeal(verificationCase.getId(), STUDENT_ID);
        events.published().clear();

        Result<VerificationCase> result = resolve(verificationCase.getId(), 3, ReviewDecision.APPROVED, "Fine.");

        assertThat(result.isSuccess()).isTrue();
        var published = (VerificationCaseResolved) events.published().get(0);
        assertThat(published.verifierUserId()).isEqualTo(3);
        assertThat(published.overturnedVerifierUserId()).isEqualTo(VERIFIER_ID);
    }

    @Test
    void resolve_anAppealedCaseAsRejected_confirmsTheFirstDecisionWithoutOverturning() {
        VerificationCase verificationCase = addRejectedCase();
        addVerifier(VERIFIER_ID);
        addVerifier(3);
        appeal(verificationCase.getId(), STUDENT_ID);
        events.published().clear();

        resolve(verificationCase.getId(), 3, ReviewDecision.REJECTED, "Still not enough.");

        var published = (VerificationCaseResolved) events.published().get(0);
        assertThat(published.decision()).isEqualTo(ReviewDecision.REJECTED);
        assertThat(published.overturnedVerifierUserId()).isNull();
    }
}
