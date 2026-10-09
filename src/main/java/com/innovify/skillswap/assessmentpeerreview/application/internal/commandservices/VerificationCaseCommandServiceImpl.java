package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerificationCaseCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AppealVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.AttachCaseEvidenceCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReassignOverdueCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ResolveVerificationCaseCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseDeadlineMissed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.VerificationCaseResolved;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Verification case command service. Not {@code @Transactional}: resolving a case saves the case and the
 * profile, and completes the node when approved, as a unit in the given {@link TransactionOperations}.
 *
 * <p>Resolving a case and reassigning it after a missed deadline both read the case with a lock inside their
 * transaction, so a verifier never resolves a case that was just taken from them, and two instances of the scheduler
 * never handle the same overdue case twice.
 */
@Service
public class VerificationCaseCommandServiceImpl implements VerificationCaseCommandService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCaseCommandServiceImpl.class);

    private final VerificationCaseRepository caseRepository;
    private final VerifierProfileRepository profileRepository;
    private final CaseAssignmentService assignmentService;
    private final LearningPathContextFacade learningPathFacade;
    private final ReviewDeadlineResolver deadlineResolver;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public VerificationCaseCommandServiceImpl(VerificationCaseRepository caseRepository,
                                              VerifierProfileRepository profileRepository,
                                              CaseAssignmentService assignmentService,
                                              LearningPathContextFacade learningPathFacade,
                                              ReviewDeadlineResolver deadlineResolver,
                                              DomainEventPublisher eventPublisher,
                                              TransactionOperations transactions,
                                              MessageSource messageSource) {
        this.caseRepository = caseRepository;
        this.profileRepository = profileRepository;
        this.assignmentService = assignmentService;
        this.learningPathFacade = learningPathFacade;
        this.deadlineResolver = deadlineResolver;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<VerificationCase> handle(AttachCaseEvidenceCommand command) {
        try {
            Optional<VerificationCase> found = caseRepository.findById(command.caseId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.CASE_NOT_FOUND);
            }
            VerificationCase verificationCase = found.get();
            if (verificationCase.getStudentId() != command.studentId()) {
                return failures.failure(AssessmentPeerReviewError.NOT_CASE_OWNER);
            }
            if (!verificationCase.isOpen()) {
                return failures.failure(AssessmentPeerReviewError.CASE_ALREADY_RESOLVED);
            }

            try {
                verificationCase.attachEvidence(command.evidenceUrl());
            } catch (DomainException exception) {
                // The only rule left to break at this point is the format of the link.
                return failures.failure(AssessmentPeerReviewError.INVALID_EVIDENCE_URL);
            }

            return Result.success(caseRepository.save(verificationCase));
        } catch (RuntimeException exception) {
            log.error("Could not attach evidence to the case {}", command.caseId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<VerificationCase> handle(ResolveVerificationCaseCommand command) {
        if (command.decision() == null) {
            return failures.failure(AssessmentPeerReviewError.INVALID_DECISION);
        }

        String notes = command.rubricNotes() == null ? "" : command.rubricNotes().strip();
        if (notes.isEmpty()) {
            return failures.failure(AssessmentPeerReviewError.RUBRIC_NOTES_REQUIRED);
        }
        if (notes.length() > VerificationCase.MAX_RUBRIC_NOTES_LENGTH) {
            return failures.failure(AssessmentPeerReviewError.RUBRIC_NOTES_TOO_LONG);
        }

        try {
            Optional<VerificationCase> found = caseRepository.findById(command.caseId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.CASE_NOT_FOUND);
            }
            VerificationCase verificationCase = found.get();
            if (!verificationCase.isAssignedTo(command.verifierUserId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_ASSIGNED_VERIFIER);
            }
            if (!verificationCase.isOpen()) {
                return failures.failure(AssessmentPeerReviewError.CASE_ALREADY_RESOLVED);
            }

            Optional<VerifierProfile> profile = profileRepository.findByUserId(command.verifierUserId());
            if (profile.isEmpty() || !profile.get().isVerified()) {
                return failures.failure(AssessmentPeerReviewError.NOT_A_VERIFIER);
            }

            VerificationCase resolved = transactions.execute(status -> {
                // Read again under the lock: the case may have been reassigned or resolved meanwhile.
                VerificationCase locked = caseRepository.findByIdForUpdate(command.caseId()).orElseThrow();
                if (!locked.isAssignedTo(command.verifierUserId())) {
                    throw new CaseChangedException(AssessmentPeerReviewError.NOT_ASSIGNED_VERIFIER);
                }
                if (!locked.isOpen()) {
                    throw new CaseChangedException(AssessmentPeerReviewError.CASE_ALREADY_RESOLVED);
                }

                locked.resolve(command.decision(), notes);
                profile.get().incrementReviewCount();
                VerificationCase stored = caseRepository.save(locked);
                profileRepository.save(profile.get());

                if (command.decision() == ReviewDecision.APPROVED) {
                    // A node that is already completed is not a problem: the student got there anyway.
                    NodeCompletionOutcome completion = learningPathFacade.completeNode(stored.getPathNodeId());
                    if (completion != NodeCompletionOutcome.COMPLETED
                            && completion != NodeCompletionOutcome.ALREADY_COMPLETED) {
                        throw new NodeCompletionFailedException(completion);
                    }
                }
                return stored;
            });

            eventPublisher.publish(new VerificationCaseResolved(resolved.getId(), resolved.getStudentId(),
                    command.verifierUserId(), resolved.getPathNodeId(), resolved.getSkillTag(),
                    resolved.getCaseType(), command.decision(), resolved.overturnedVerifierUserId()));

            return Result.success(resolved);
        } catch (CaseChangedException exception) {
            return failures.failure(exception.error());
        } catch (NodeCompletionFailedException exception) {
            return failures.failure(AssessmentPeerReviewFailures.fromNodeCompletion(exception.outcome()));
        } catch (RuntimeException exception) {
            log.error("Could not resolve the case {}", command.caseId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<VerificationCase> handle(AppealVerificationCaseCommand command) {
        try {
            Optional<VerificationCase> found = caseRepository.findById(command.caseId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.CASE_NOT_FOUND);
            }
            VerificationCase verificationCase = found.get();
            if (verificationCase.getStudentId() != command.studentId()) {
                return failures.failure(AssessmentPeerReviewError.NOT_CASE_OWNER);
            }
            if (!verificationCase.canBeAppealed()) {
                // A rejected case with no appeals left is a different problem from one that cannot be appealed.
                boolean rejected = !verificationCase.isOpen()
                        && verificationCase.getDecision() == ReviewDecision.REJECTED;
                return failures.failure(rejected
                        ? AssessmentPeerReviewError.APPEAL_ALREADY_USED
                        : AssessmentPeerReviewError.CASE_NOT_APPEALABLE);
            }
            // The student may have retaken the assessment: reopening this case would be a second open one.
            if (caseRepository.findOpenByStudentAndNode(command.studentId(),
                    verificationCase.getPathNodeId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }

            verificationCase.appeal();
            assignmentService.tryAssign(verificationCase);
            return Result.success(caseRepository.save(verificationCase));
        } catch (RuntimeException exception) {
            log.error("Could not appeal the case {}", command.caseId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<VerificationCase> handle(ReassignOverdueCaseCommand command) {
        try {
            Instant now = Instant.now();
            Reassignment reassignment = transactions.execute(status -> {
                Optional<VerificationCase> found = caseRepository.findByIdForUpdate(command.caseId());
                if (found.isEmpty()) {
                    return null;
                }
                VerificationCase verificationCase = found.get();
                // Resolved, or already reassigned with a new deadline, by someone else in the meantime.
                if (!verificationCase.isOverdue(now)) {
                    return new Reassignment(verificationCase, null, null);
                }

                int verifierWhoMissedIt = verificationCase.getVerifierUserId();
                boolean newBreach = verificationCase.recordMissedDeadline(now);
                Instant missedDueAt = verificationCase.getReviewDueAt();
                Optional<Integer> replacement = assignmentService.findReplacementVerifier(verificationCase);
                if (replacement.isPresent()) {
                    verificationCase.reassignAfterMissedDeadline(replacement.get(),
                            deadlineResolver.forStudent(verificationCase.getStudentId()), now);
                }
                VerificationCase stored = caseRepository.save(verificationCase);
                VerificationCaseDeadlineMissed breach = newBreach
                        ? new VerificationCaseDeadlineMissed(stored.getId(), verifierWhoMissedIt,
                                stored.getStudentId(), missedDueAt, replacement.orElse(null))
                        : null;
                return new Reassignment(stored, breach, replacement.orElse(null));
            });

            if (reassignment == null) {
                return failures.failure(AssessmentPeerReviewError.CASE_NOT_FOUND);
            }
            if (reassignment.breach() != null) {
                eventPublisher.publish(reassignment.breach());
            }
            if (reassignment.newVerifierUserId() != null) {
                log.info("The overdue case {} was reassigned to the verifier {}", command.caseId(),
                        reassignment.newVerifierUserId());
            }
            return Result.success(reassignment.verificationCase());
        } catch (RuntimeException exception) {
            log.error("Could not reassign the overdue case {}", command.caseId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /** What handling an overdue case did: the breach to announce, if new, and who took the case, if anybody. */
    private record Reassignment(VerificationCase verificationCase, VerificationCaseDeadlineMissed breach,
                                Integer newVerifierUserId) {
    }

    /** Thrown inside a transaction when the case changed after the checks, so nothing is saved. */
    private static final class CaseChangedException extends RuntimeException {

        private final transient AssessmentPeerReviewError error;

        CaseChangedException(AssessmentPeerReviewError error) {
            super("The case changed: " + error);
            this.error = error;
        }

        AssessmentPeerReviewError error() {
            return error;
        }
    }
}
