package com.innovify.skillswap.reputation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.reputation.application.fakes.FakeStudentEmployabilityScoreRepository;
import com.innovify.skillswap.reputation.application.fakes.FakeVerifierProfileContextFacade;
import com.innovify.skillswap.reputation.application.fakes.FakeVerifierProfileContextFacade.Update;
import com.innovify.skillswap.reputation.application.fakes.FakeVerifierReliabilityRepository;
import com.innovify.skillswap.reputation.application.fakes.TestMessages;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.commands.RecordAutomaticApprovalCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordCaseResolutionCommand;
import com.innovify.skillswap.reputation.domain.model.commands.RecordOverturnCommand;
import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.shared.application.Result;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

class ReputationCommandServiceImplTest {

    private final FakeVerifierReliabilityRepository reliabilities = new FakeVerifierReliabilityRepository();
    private final FakeStudentEmployabilityScoreRepository employabilities = new FakeStudentEmployabilityScoreRepository();
    private final FakeVerifierProfileContextFacade facade = new FakeVerifierProfileContextFacade();
    private ReputationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new ReputationCommandServiceImpl(reliabilities, employabilities,
                new DefaultVerifierReliabilityCalculator(), new DefaultEmployabilityScoreCalculator(), facade,
                TransactionOperations.withoutTransaction(), TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<VerifierReliability> resolve(boolean approved, int verifierUserId, int studentId) {
        return service.handle(new RecordCaseResolutionCommand(verifierUserId, studentId, approved));
    }

    private Result<VerifierReliability> resolve(boolean approved) {
        return resolve(approved, 2, 1);
    }

    private Result<VerifierReliability> overturn(int verifierUserId) {
        return service.handle(new RecordOverturnCommand(verifierUserId));
    }

    private Result<StudentEmployabilityScore> approveAutomatically(int studentId) {
        return service.handle(new RecordAutomaticApprovalCommand(studentId));
    }

    private static void assertFailure(Result<?> result, ReputationError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
    }

    // ---------- Case resolution ----------

    @Test
    void resolution_approved_countsTheCaseCertifiesTheSkillAndSyncsTheRating() {
        Result<VerifierReliability> result = resolve(true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(reliabilities.items()).hasSize(1);
        VerifierReliability reliability = reliabilities.items().get(0);
        assertThat(result.value()).isSameAs(reliability);
        assertThat(reliability.getVerifierUserId()).isEqualTo(2);
        assertThat(reliability.getResolvedCasesCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(100);
        assertThat(employabilities.items()).hasSize(1);
        StudentEmployabilityScore employability = employabilities.items().get(0);
        assertThat(employability.getStudentId()).isEqualTo(1);
        assertThat(employability.getVerifiedSkillsCount()).isEqualTo(1);
        assertThat(employability.getScore().value()).isEqualTo(10);
        assertThat(facade.updates()).containsExactly(new Update(2, 100d));
    }

    @Test
    void resolution_rejected_countsTheCaseButCertifiesNothing() {
        Result<VerifierReliability> result = resolve(false);

        assertThat(result.isSuccess()).isTrue();
        assertThat(reliabilities.items().get(0).getResolvedCasesCount()).isEqualTo(1);
        assertThat(employabilities.items()).isEmpty();
        assertThat(facade.updates()).hasSize(1);
    }

    @Test
    void resolution_repeated_updatesTheSameRecordsInsteadOfCreatingNewOnes() {
        resolve(true, 2, 1);
        resolve(true, 2, 1);
        resolve(false, 2, 5);

        assertThat(reliabilities.items()).hasSize(1);
        assertThat(reliabilities.items().get(0).getResolvedCasesCount()).isEqualTo(3);
        assertThat(employabilities.items()).hasSize(1);
        assertThat(employabilities.items().get(0).getVerifiedSkillsCount()).isEqualTo(2);
        assertThat(employabilities.items().get(0).getScore().value()).isEqualTo(20);
    }

    @Test
    void resolution_keepsTheReliabilityOfEachVerifierSeparate() {
        resolve(true, 2, 1);
        resolve(true, 3, 1);

        assertThat(reliabilities.items()).hasSize(2);
        assertThat(reliabilities.items()).allMatch(r -> r.getResolvedCasesCount() == 1);
    }

    @Test
    void resolution_syncsTheCurrentScoreOfAnExistingReliability() {
        reliabilities.save(new VerifierReliability(2).recordOverturn(new DefaultVerifierReliabilityCalculator()));

        resolve(false);

        assertThat(facade.updates()).containsExactly(new Update(2, 85d));
    }

    @Test
    void resolution_forAUserWithoutAVerifierProfile_stillSucceeds() {
        facade.setUpdated(false);

        assertThat(resolve(true).isSuccess()).isTrue();
    }

    @Test
    void resolution_whenTheRatingSyncFails_stillSucceedsAndKeepsTheReputation() {
        facade.failWith(new IllegalStateException("sync failed"));

        Result<VerifierReliability> result = resolve(true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(reliabilities.items()).hasSize(1);
        assertThat(employabilities.items()).hasSize(1);
    }

    @Test
    void resolution_whenPersistenceFails_failsWithDatabaseErrorAndDoesNotSync() {
        reliabilities.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(resolve(true), ReputationError.DATABASE_ERROR);
        assertThat(facade.updates()).isEmpty();
    }

    @Test
    void resolution_withAnInvalidUser_failsWithInternalServerErrorAndSavesNothing() {
        assertFailure(resolve(true, 0, 1), ReputationError.INTERNAL_SERVER_ERROR);
        assertThat(reliabilities.items()).isEmpty();
        assertThat(reliabilities.saveCalls()).isZero();
    }

    // ---------- Overturned decisions ----------

    @Test
    void overturn_discountsTheReliabilityAndSyncsTheRating() {
        Result<VerifierReliability> result = overturn(2);

        assertThat(result.isSuccess()).isTrue();
        VerifierReliability reliability = reliabilities.items().get(0);
        assertThat(reliability.getOverturnedDecisionsCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(85);
        assertThat(facade.updates()).containsExactly(new Update(2, 85d));
    }

    @Test
    void overturn_ofAVerifierWhoAlreadyResolvedCases_updatesTheSameRecord() {
        resolve(true, 2, 1);
        resolve(false, 2, 5);

        overturn(2);
        overturn(2);

        assertThat(reliabilities.items()).hasSize(1);
        VerifierReliability reliability = reliabilities.items().get(0);
        assertThat(reliability.getResolvedCasesCount()).isEqualTo(2);
        assertThat(reliability.getOverturnedDecisionsCount()).isEqualTo(2);
        assertThat(reliability.getScore().value()).isEqualTo(70);
    }

    @Test
    void overturn_doesNotTouchTheEmployabilityOfAnyStudent() {
        overturn(2);

        assertThat(employabilities.items()).isEmpty();
    }

    @Test
    void overturn_whenPersistenceFails_failsWithDatabaseErrorAndDoesNotSync() {
        reliabilities.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(overturn(2), ReputationError.DATABASE_ERROR);
        assertThat(facade.updates()).isEmpty();
    }

    @Test
    void overturn_withAnInvalidUser_failsWithInternalServerError() {
        assertFailure(overturn(0), ReputationError.INTERNAL_SERVER_ERROR);
        assertThat(reliabilities.items()).isEmpty();
    }

    // ---------- Automatic approval ----------

    @Test
    void automaticApproval_certifiesTheSkillOfTheStudent() {
        Result<StudentEmployabilityScore> result = approveAutomatically(1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(employabilities.items()).hasSize(1);
        StudentEmployabilityScore employability = employabilities.items().get(0);
        assertThat(result.value()).isSameAs(employability);
        assertThat(employability.getVerifiedSkillsCount()).isEqualTo(1);
        assertThat(employability.getScore().value()).isEqualTo(10);
        assertThat(reliabilities.items()).isEmpty();
        assertThat(facade.updates()).isEmpty();
    }

    @Test
    void automaticApproval_repeated_accumulatesOnTheSameRecord() {
        approveAutomatically(1);
        approveAutomatically(1);
        approveAutomatically(7);

        assertThat(employabilities.items()).hasSize(2);
        assertThat(employabilities.findByStudentId(1).orElseThrow().getScore().value()).isEqualTo(20);
        assertThat(employabilities.findByStudentId(7).orElseThrow().getScore().value()).isEqualTo(10);
    }

    @Test
    void automaticApproval_whenPersistenceFails_failsWithDatabaseError() {
        employabilities.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(approveAutomatically(1), ReputationError.DATABASE_ERROR);
    }

    @Test
    void automaticApproval_withAnInvalidStudent_failsWithInternalServerError() {
        assertFailure(approveAutomatically(0), ReputationError.INTERNAL_SERVER_ERROR);
        assertThat(employabilities.items()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void missedDeadline_isCountedAndTheRatingFollows() {
        var result = service.handle(new com.innovify.skillswap.reputation.domain.model.commands
                .RecordMissedDeadlineCommand(4));

        org.assertj.core.api.Assertions.assertThat(result.value().getMissedDeadlinesCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(result.value().getScore().value()).isEqualTo(95);
        org.assertj.core.api.Assertions.assertThat(facade.updates()).containsExactly(
                new com.innovify.skillswap.reputation.application.fakes.FakeVerifierProfileContextFacade.Update(4, 95));
    }
}
