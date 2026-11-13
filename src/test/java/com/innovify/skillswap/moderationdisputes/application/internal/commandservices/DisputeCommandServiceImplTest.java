package com.innovify.skillswap.moderationdisputes.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewOutcome;
import com.innovify.skillswap.moderationdisputes.application.fakes.FakeDisputeRepository;
import com.innovify.skillswap.moderationdisputes.application.fakes.FakeModerationFacades;
import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.AssignPendingDisputesCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.EscalateCertificateReviewCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.ResolveDisputeCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeResolutionValidator;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeReviewerSelector;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.support.TransactionOperations;

class DisputeCommandServiceImplTest {

    private static final int OWNER = 7;
    private static final int CERTIFICATE = 30;

    private final FakeDisputeRepository disputes = new FakeDisputeRepository();
    private final FakeModerationFacades.Verifiers verifiers = new FakeModerationFacades.Verifiers();
    private final FakeModerationFacades.Reputation reputation = new FakeModerationFacades.Reputation();
    private final FakeModerationFacades.Credentials credentials = new FakeModerationFacades.Credentials();
    private DisputeCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new DisputeCommandServiceImpl(disputes, new DisputeResolutionValidator(),
                new DisputeReviewerSelector(), verifiers, reputation, credentials,
                TransactionOperations.withoutTransaction(), TestMessages.source());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Dispute escalate() {
        var result = service.handle(new EscalateCertificateReviewCommand(CERTIFICATE, OWNER,
                List.of("DuplicateFile")));
        assertThat(result.isSuccess()).isTrue();
        return result.value();
    }

    @Test
    void escalate_assignsTheLeastLoadedSeniorWhoIsNotTheOwner() {
        verifiers.add(OWNER, 0).add(2, 0).add(3, 4).add(4, 1);
        reputation.senior(OWNER).senior(3).senior(4);

        Dispute dispute = escalate();

        assertThat(dispute.getAssignedVerifierUserId()).isEqualTo(4);
        assertThat(dispute.isAssignedToSenior()).isTrue();
        assertThat(dispute.getRespondentUserId()).isEqualTo(OWNER);
    }

    @Test
    void escalate_countsThePendingDisputesInTheWorkload() {
        verifiers.add(3, 0).add(4, 0);
        reputation.senior(3).senior(4);

        assertThat(service.handle(new EscalateCertificateReviewCommand(1, OWNER, List.of())).value()
                .getAssignedVerifierUserId()).isEqualTo(3);
        assertThat(service.handle(new EscalateCertificateReviewCommand(2, OWNER, List.of())).value()
                .getAssignedVerifierUserId()).isEqualTo(4);
    }

    @Test
    void escalate_withoutSeniors_fallsBackToAnotherVerifier() {
        verifiers.add(OWNER, 0).add(5, 2);

        Dispute dispute = escalate();

        assertThat(dispute.getAssignedVerifierUserId()).isEqualTo(5);
        assertThat(dispute.isAssignedToSenior()).isFalse();
    }

    @Test
    void escalate_withNobodyAvailable_waitsWithoutReviewer() {
        verifiers.add(OWNER, 0);

        Dispute dispute = escalate();

        assertThat(dispute.hasReviewer()).isFalse();
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.PENDING);
    }

    @Test
    void escalate_theSameCertificateAgain_answersTheSameDispute() {
        Dispute first = escalate();
        Dispute second = escalate();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(disputes.disputes()).hasSize(1);
    }

    @Test
    void assignPending_givesTheWaitingDisputesToTheVerifiersAvailableNow() {
        escalate();
        verifiers.add(9, 0);

        var result = service.handle(new AssignPendingDisputesCommand());

        assertThat(result.value()).isEqualTo(1);
        assertThat(disputes.disputes().getFirst().getAssignedVerifierUserId()).isEqualTo(9);
        assertThat(disputes.locked()).containsExactly(1);
        assertThat(service.handle(new AssignPendingDisputesCommand()).value()).isZero();
    }

    @Test
    void resolve_upheld_verifiesTheCertificate() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();

        var result = service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.UPHELD, "Legit"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(credentials.decisions()).containsExactly(
                new FakeModerationFacades.Credentials.Decision(CERTIFICATE, true, "Legit"));
    }

    @Test
    void resolve_overturned_rejectsTheCertificate() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();

        service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.OVERTURNED, "Fraud"));

        assertThat(credentials.decisions()).containsExactly(
                new FakeModerationFacades.Credentials.Decision(CERTIFICATE, false, "Fraud"));
    }

    @Test
    void resolve_rejectsInvalidRequests() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();
        int id = dispute.getId();

        assertError(service.handle(new ResolveDisputeCommand(id, 4, null, "x")).error(),
                ModerationDisputesError.INVALID_OUTCOME);
        assertError(service.handle(new ResolveDisputeCommand(id, 4, DisputeOutcome.UPHELD, " ")).error(),
                ModerationDisputesError.RESOLUTION_NOTES_REQUIRED);
        assertError(service.handle(new ResolveDisputeCommand(id, 4, DisputeOutcome.UPHELD, "x".repeat(2001))).error(),
                ModerationDisputesError.RESOLUTION_NOTES_TOO_LONG);
        assertError(service.handle(new ResolveDisputeCommand(99, 4, DisputeOutcome.UPHELD, "x")).error(),
                ModerationDisputesError.DISPUTE_NOT_FOUND);
        assertError(service.handle(new ResolveDisputeCommand(id, OWNER, DisputeOutcome.UPHELD, "x")).error(),
                ModerationDisputesError.NOT_ASSIGNED_REVIEWER);
        assertError(service.handle(new ResolveDisputeCommand(id, 4, DisputeOutcome.DISMISSED, "x")).error(),
                ModerationDisputesError.INVALID_OUTCOME);
        assertThat(credentials.decisions()).isEmpty();
        assertThat(dispute.isPending()).isTrue();
    }

    @Test
    void resolve_aCertificateThatIsNoLongerSuspicious_isAConflict() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();
        credentials.answer(CertificateReviewOutcome.NOT_SUSPICIOUS);

        var result = service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.UPHELD, "x"));

        assertError(result.error(), ModerationDisputesError.CERTIFICATE_NOT_SUSPICIOUS);
    }

    @Test
    void resolve_whenTheCertificateCannotBeSaved_isAnInternalError() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();
        credentials.answer(CertificateReviewOutcome.FAILED);

        var result = service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.UPHELD, "x"));

        assertError(result.error(), ModerationDisputesError.INTERNAL_SERVER_ERROR);
    }

    @Test
    void resolve_twice_isAConflict() {
        verifiers.add(4, 0);
        Dispute dispute = escalate();
        service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.UPHELD, "x"));

        var again = service.handle(new ResolveDisputeCommand(dispute.getId(), 4, DisputeOutcome.UPHELD, "x"));

        assertError(again.error(), ModerationDisputesError.DISPUTE_ALREADY_RESOLVED);
        assertThat(credentials.decisions()).hasSize(1);
    }

    private static void assertError(Enum<?> actual, ModerationDisputesError expected) {
        assertThat(actual).isEqualTo(expected);
    }
}
