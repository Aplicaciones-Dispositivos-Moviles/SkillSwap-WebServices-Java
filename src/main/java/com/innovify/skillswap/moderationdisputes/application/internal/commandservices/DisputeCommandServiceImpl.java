package com.innovify.skillswap.moderationdisputes.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierProfileContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierWorkload;
import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewOutcome;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.moderationdisputes.application.commandservices.DisputeCommandService;
import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.AssignPendingDisputesCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.EscalateCertificateReviewCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.ResolveDisputeCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.repositories.DisputeRepository;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeResolutionValidator;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeReviewerSelector;
import com.innovify.skillswap.moderationdisputes.domain.services.ReviewerCandidate;
import com.innovify.skillswap.moderationdisputes.domain.services.ReviewerChoice;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Dispute command service. Not {@code @Transactional}: each change runs in the given {@link TransactionOperations},
 * which must start a new transaction because the escalations come from event handlers that may run after the commit
 * of the transaction that raised the event. A dispute is locked before it is assigned or resolved, so two requests
 * never change it at once, and resolving a certificate review changes the certificate in the same transaction.
 *
 * <p>The reviewer is chosen among the available verifiers of Assessment &amp; Peer Review; who is a Verificador
 * senior is asked to Reputation. Their workload counts their open verification cases and pending disputes.
 */
public class DisputeCommandServiceImpl implements DisputeCommandService {

    private static final Logger log = LoggerFactory.getLogger(DisputeCommandServiceImpl.class);

    private final DisputeRepository disputeRepository;
    private final DisputeResolutionValidator resolutionValidator;
    private final DisputeReviewerSelector reviewerSelector;
    private final VerifierProfileContextFacade verifierFacade;
    private final ReputationContextFacade reputationFacade;
    private final CredentialContextFacade credentialFacade;
    private final TransactionOperations transactions;
    private final ModerationDisputesFailures failures;

    public DisputeCommandServiceImpl(DisputeRepository disputeRepository,
                                     DisputeResolutionValidator resolutionValidator,
                                     DisputeReviewerSelector reviewerSelector,
                                     VerifierProfileContextFacade verifierFacade,
                                     ReputationContextFacade reputationFacade,
                                     CredentialContextFacade credentialFacade,
                                     TransactionOperations transactions,
                                     MessageSource messageSource) {
        this.disputeRepository = disputeRepository;
        this.resolutionValidator = resolutionValidator;
        this.reviewerSelector = reviewerSelector;
        this.verifierFacade = verifierFacade;
        this.reputationFacade = reputationFacade;
        this.credentialFacade = credentialFacade;
        this.transactions = transactions;
        this.failures = new ModerationDisputesFailures(messageSource);
    }

    @Override
    public Result<Dispute> handle(EscalateCertificateReviewCommand command) {
        try {
            Optional<Dispute> existing = disputeRepository.findBySource(DisputeSourceType.CERTIFICATE_REVIEW,
                    command.certificateId());
            if (existing.isPresent()) {
                return Result.success(existing.get());
            }

            Dispute opened = Dispute.certificateReview(command.certificateId(), command.ownerId(), command.reasons());
            Dispute saved = transactions.execute(status -> {
                tryAssign(opened);
                return disputeRepository.save(opened);
            });
            log.info("The suspicious certificate {} was escalated as dispute {} (reviewer {}, senior {})",
                    command.certificateId(), saved.getId(), saved.getAssignedVerifierUserId(),
                    saved.isAssignedToSenior());
            return Result.success(saved);
        } catch (DataIntegrityViolationException exception) {
            // The same certificate was escalated at the same time by another delivery of the event.
            Optional<Dispute> concurrent = disputeRepository.findBySource(DisputeSourceType.CERTIFICATE_REVIEW,
                    command.certificateId());
            if (concurrent.isPresent()) {
                return Result.success(concurrent.get());
            }
            log.error("Could not escalate the certificate {}", command.certificateId(), exception);
            return failures.failure(ModerationDisputesFailures.toError(exception));
        } catch (RuntimeException exception) {
            log.error("Could not escalate the certificate {}", command.certificateId(), exception);
            return failures.failure(ModerationDisputesFailures.toError(exception));
        }
    }

    @Override
    public Result<Dispute> handle(ResolveDisputeCommand command) {
        if (command.outcome() == null) {
            return failures.failure(ModerationDisputesError.INVALID_OUTCOME);
        }
        String notes = command.resolutionNotes() == null ? "" : command.resolutionNotes().strip();
        if (notes.isEmpty()) {
            return failures.failure(ModerationDisputesError.RESOLUTION_NOTES_REQUIRED);
        }
        if (notes.length() > Dispute.MAX_RESOLUTION_NOTES_LENGTH) {
            return failures.failure(ModerationDisputesError.RESOLUTION_NOTES_TOO_LONG);
        }

        try {
            Optional<Dispute> found = disputeRepository.findById(command.disputeId());
            if (found.isEmpty()) {
                return failures.failure(ModerationDisputesError.DISPUTE_NOT_FOUND);
            }
            Optional<ModerationDisputesError> rejection = check(found.get(), command);
            if (rejection.isPresent()) {
                return failures.failure(rejection.get());
            }

            Dispute resolved = transactions.execute(status -> {
                // Read again under the lock: the dispute may have been resolved meanwhile.
                Dispute dispute = disputeRepository.findByIdForUpdate(command.disputeId()).orElseThrow();
                check(dispute, command).ifPresent(error -> {
                    throw new DisputeResolutionAbortedException(error);
                });

                dispute.resolve(command.outcome(), notes, resolutionValidator);
                Dispute stored = disputeRepository.save(dispute);
                if (stored.getSourceType() == DisputeSourceType.CERTIFICATE_REVIEW) {
                    applyToCertificate(stored);
                }
                return stored;
            });
            return Result.success(resolved);
        } catch (DisputeResolutionAbortedException exception) {
            return failures.failure(exception.error());
        } catch (RuntimeException exception) {
            log.error("Could not resolve the dispute {}", command.disputeId(), exception);
            return failures.failure(ModerationDisputesFailures.toError(exception));
        }
    }

    @Override
    public Result<Integer> handle(AssignPendingDisputesCommand command) {
        int assigned = 0;
        try {
            for (Integer disputeId : disputeRepository.findUnassignedPendingIds()) {
                Boolean done = transactions.execute(status -> {
                    Dispute dispute = disputeRepository.findByIdForUpdate(disputeId).orElse(null);
                    if (dispute == null || !dispute.isPending() || dispute.hasReviewer() || !tryAssign(dispute)) {
                        return false;
                    }
                    disputeRepository.save(dispute);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) {
                    assigned++;
                }
            }
            return Result.success(assigned);
        } catch (RuntimeException exception) {
            log.error("Could not assign the pending disputes ({} assigned before the failure)", assigned, exception);
            return failures.failure(ModerationDisputesFailures.toError(exception));
        }
    }

    /** Why the reviewer cannot resolve the dispute, if they cannot. */
    private Optional<ModerationDisputesError> check(Dispute dispute, ResolveDisputeCommand command) {
        if (!dispute.isAssignedTo(command.verifierUserId())) {
            return Optional.of(ModerationDisputesError.NOT_ASSIGNED_REVIEWER);
        }
        if (!resolutionValidator.canResolve(dispute)) {
            return Optional.of(ModerationDisputesError.DISPUTE_ALREADY_RESOLVED);
        }
        if (!resolutionValidator.isValidOutcome(dispute.getSourceType(), command.outcome())) {
            return Optional.of(ModerationDisputesError.INVALID_OUTCOME);
        }
        return Optional.empty();
    }

    /**
     * Upheld: the certificate is legitimate (Verified); Overturned: it is fraudulent (Rejected). The resolution notes
     * of the reviewer are the reason the student receives with the push notification of a rejection (US16).
     */
    private void applyToCertificate(Dispute dispute) {
        boolean authentic = dispute.getOutcome() == DisputeOutcome.UPHELD;
        CertificateReviewOutcome outcome = credentialFacade.resolveSuspiciousCertificate(
                dispute.getSourceReferenceId(), authentic, dispute.getResolutionNotes());
        switch (outcome) {
            case RESOLVED -> {
                // Done: the certificate is in its final state.
            }
            case NOT_SUSPICIOUS -> throw new DisputeResolutionAbortedException(
                    ModerationDisputesError.CERTIFICATE_NOT_SUSPICIOUS);
            case NOT_FOUND, FAILED -> throw new IllegalStateException(
                    "The certificate %d could not be resolved: %s".formatted(dispute.getSourceReferenceId(), outcome));
        }
    }

    /**
     * Assigns the dispute to a senior or, when no senior is available, to another available verifier. The dispute is
     * not saved: the caller does it.
     *
     * @return whether a reviewer was found
     */
    private boolean tryAssign(Dispute dispute) {
        List<VerifierWorkload> available = verifierFacade.getAvailableVerifiers();
        if (available.isEmpty()) {
            return false;
        }

        List<Integer> userIds = available.stream().map(VerifierWorkload::verifierUserId).toList();
        Set<Integer> seniors = reputationFacade.findSeniorVerifiers(userIds);
        Map<Integer, Integer> pendingDisputes = disputeRepository.countPendingByVerifierUserIds(userIds);
        List<ReviewerCandidate> candidates = available.stream()
                .map(verifier -> new ReviewerCandidate(verifier.verifierUserId(),
                        seniors.contains(verifier.verifierUserId()),
                        verifier.openCaseCount() + pendingDisputes.getOrDefault(verifier.verifierUserId(), 0)))
                .toList();

        List<Integer> parties = new ArrayList<>();
        if (dispute.getRespondentUserId() != null) {
            parties.add(dispute.getRespondentUserId());
        }
        if (dispute.getRaisedByUserId() != null) {
            parties.add(dispute.getRaisedByUserId());
        }

        Optional<ReviewerChoice> choice = reviewerSelector.choose(parties, candidates);
        choice.ifPresent(chosen -> dispute.assignReviewer(chosen.userId(), chosen.senior()));
        return choice.isPresent();
    }
}
