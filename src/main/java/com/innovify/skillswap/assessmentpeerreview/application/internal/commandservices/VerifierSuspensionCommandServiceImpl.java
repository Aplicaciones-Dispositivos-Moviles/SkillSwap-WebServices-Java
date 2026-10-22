package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierSuspensionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReinstateVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SuspendVerifierCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Verifier suspension command service. Only a Verificador senior (an enabled verifier with the Gold rank and a
 * reliability of 90 or more, as Reputation calculates it) suspends and reinstates, and only a verifier whose
 * reliability is below {@value #LOW_RELIABILITY_THRESHOLD} can be suspended.
 */
@Service
public class VerifierSuspensionCommandServiceImpl implements VerifierSuspensionCommandService {

    /** A verifier with a reliability below this value can be suspended. */
    public static final int LOW_RELIABILITY_THRESHOLD = 60;

    private static final Logger log = LoggerFactory.getLogger(VerifierSuspensionCommandServiceImpl.class);

    private final VerifierProfileRepository profileRepository;
    private final VerificationCaseRepository caseRepository;
    private final CaseAssignmentService assignmentService;
    private final ReputationContextFacade reputationFacade;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public VerifierSuspensionCommandServiceImpl(VerifierProfileRepository profileRepository,
                                                VerificationCaseRepository caseRepository,
                                                CaseAssignmentService assignmentService,
                                                ReputationContextFacade reputationFacade,
                                                TransactionOperations transactions,
                                                MessageSource messageSource) {
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
        this.assignmentService = assignmentService;
        this.reputationFacade = reputationFacade;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<VerifierProfile> handle(SuspendVerifierCommand command) {
        try {
            if (!isSenior(command.seniorUserId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
            }

            String justification = command.justification() == null ? "" : command.justification().strip();
            if (justification.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.JUSTIFICATION_REQUIRED);
            }
            if (justification.length() > VerifierProfile.MAX_SUSPENSION_REASON_LENGTH) {
                return failures.failure(AssessmentPeerReviewError.JUSTIFICATION_TOO_LONG);
            }
            if (command.verifierUserId() == command.seniorUserId()) {
                return failures.failure(AssessmentPeerReviewError.CANNOT_SUSPEND_SELF);
            }

            Optional<VerifierProfile> found = profileRepository.findByUserId(command.verifierUserId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.VERIFIER_PROFILE_NOT_FOUND);
            }
            VerifierProfile profile = found.get();
            if (profile.isSuspended()) {
                return failures.failure(AssessmentPeerReviewError.ALREADY_SUSPENDED);
            }
            OptionalInt reliability = reputationFacade.reliabilityOf(command.verifierUserId());
            if (reliability.isEmpty() || reliability.getAsInt() >= LOW_RELIABILITY_THRESHOLD) {
                return failures.failure(AssessmentPeerReviewError.NOT_LOW_RELIABILITY);
            }

            Instant now = Instant.now();
            SuspensionOutcome outcome = transactions.execute(status -> {
                VerifierProfile saved = profileRepository.save(
                        profile.suspend(command.seniorUserId(), justification, now));
                return releaseOpenCases(saved, now);
            });
            log.info("The senior {} suspended the verifier {} (reliability {}): {} cases released, {} reassigned",
                    command.seniorUserId(), command.verifierUserId(), reliability.getAsInt(),
                    outcome.released(), outcome.reassigned());
            return Result.success(outcome.profile());
        } catch (RuntimeException exception) {
            log.error("Could not suspend the verifier {}", command.verifierUserId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<VerifierProfile> handle(ReinstateVerifierCommand command) {
        try {
            if (!isSenior(command.seniorUserId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
            }

            Optional<VerifierProfile> found = profileRepository.findByUserId(command.verifierUserId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.VERIFIER_PROFILE_NOT_FOUND);
            }
            VerifierProfile profile = found.get();
            if (!profile.isSuspended()) {
                return failures.failure(AssessmentPeerReviewError.NOT_SUSPENDED);
            }

            VerifierProfile reinstated = transactions.execute(status -> {
                VerifierProfile saved = profileRepository.save(profile.reinstate());
                // The cases of its skills that were waiting for a verifier can go to it now.
                assignmentService.assignPending(saved.getSkillTags());
                return saved;
            });
            log.info("The senior {} reinstated the verifier {}", command.seniorUserId(), command.verifierUserId());
            return Result.success(reinstated);
        } catch (RuntimeException exception) {
            log.error("Could not reinstate the verifier {}", command.verifierUserId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /** Only an enabled (not revoked and not suspended) verifier who is a Verificador senior. */
    private boolean isSenior(int userId) {
        boolean enabled = profileRepository.findByUserId(userId)
                .map(profile -> profile.isVerified() && !profile.isSuspended())
                .orElse(false);
        return enabled && reputationFacade.isSeniorVerifier(userId);
    }

    /** Takes back the cases in progress of the suspended verifier and gives them to others when somebody qualifies. */
    private SuspensionOutcome releaseOpenCases(VerifierProfile suspended, Instant now) {
        int released = 0;
        int reassigned = 0;
        for (Integer caseId : caseRepository.findAssignedIdsByVerifierUserId(suspended.getVerifierUserId())) {
            Optional<VerificationCase> locked = caseRepository.findByIdForUpdate(caseId);
            // Resolved or moved by someone else since it was listed.
            if (locked.isEmpty() || !locked.get().isAssignedTo(suspended.getVerifierUserId())
                    || !locked.get().isOpen()) {
                continue;
            }

            VerificationCase verificationCase = locked.get().releaseFromSuspendedVerifier(now);
            released++;
            if (assignmentService.tryAssign(verificationCase)) {
                reassigned++;
            }
            caseRepository.save(verificationCase);
        }
        return new SuspensionOutcome(suspended, released, reassigned);
    }

    private record SuspensionOutcome(VerifierProfile profile, int released, int reassigned) {
    }
}
