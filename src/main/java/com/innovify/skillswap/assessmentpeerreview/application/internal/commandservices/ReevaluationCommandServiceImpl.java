package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.ReevaluationCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.OrderReevaluationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.NodeReopenOutcome;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reevaluation command service. Not {@code @Transactional}: the node is reopened, the case is reopened and
 * reassigned (when the source is a case) and the reevaluation is recorded as a unit in the given
 * {@link TransactionOperations}.
 *
 * <p>The senior is the same kind of verifier who defines the review deadlines: an enabled verifier whom Reputation
 * considers senior. The original verifier is not penalized and keeps their credits: the reevaluation is not an
 * appeal. A senior cannot order the reevaluation of their own result.
 */
@Service
public class ReevaluationCommandServiceImpl implements ReevaluationCommandService {

    private static final Logger log = LoggerFactory.getLogger(ReevaluationCommandServiceImpl.class);

    private final AssessmentAttemptRepository attemptRepository;
    private final VerificationCaseRepository caseRepository;
    private final VerifierProfileRepository profileRepository;
    private final ReevaluationRepository reevaluationRepository;
    private final ReputationContextFacade reputationFacade;
    private final LearningPathContextFacade learningPathFacade;
    private final CaseAssignmentService assignmentService;
    private final ReviewDeadlineResolver deadlineResolver;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public ReevaluationCommandServiceImpl(AssessmentAttemptRepository attemptRepository,
                                          VerificationCaseRepository caseRepository,
                                          VerifierProfileRepository profileRepository,
                                          ReevaluationRepository reevaluationRepository,
                                          ReputationContextFacade reputationFacade,
                                          LearningPathContextFacade learningPathFacade,
                                          CaseAssignmentService assignmentService,
                                          ReviewDeadlineResolver deadlineResolver,
                                          TransactionOperations transactions,
                                          MessageSource messageSource) {
        this.attemptRepository = attemptRepository;
        this.caseRepository = caseRepository;
        this.profileRepository = profileRepository;
        this.reevaluationRepository = reevaluationRepository;
        this.reputationFacade = reputationFacade;
        this.learningPathFacade = learningPathFacade;
        this.assignmentService = assignmentService;
        this.deadlineResolver = deadlineResolver;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<Reevaluation> handle(OrderReevaluationCommand command) {
        try {
            boolean enabledVerifier = profileRepository.findByUserId(command.seniorUserId())
                    .map(VerifierProfile::isVerified)
                    .orElse(false);
            if (!enabledVerifier || !reputationFacade.isSeniorVerifier(command.seniorUserId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
            }
            if (command.sourceType() == null) {
                return failures.failure(AssessmentPeerReviewError.INVALID_SOURCE_TYPE);
            }
            String justification = command.justification() == null ? "" : command.justification().strip();
            if (justification.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.JUSTIFICATION_REQUIRED);
            }
            if (justification.length() > Reevaluation.MAX_JUSTIFICATION_LENGTH) {
                return failures.failure(AssessmentPeerReviewError.JUSTIFICATION_TOO_LONG);
            }

            Source source = command.sourceType() == ReevaluationSourceType.ATTEMPT
                    ? sourceOfAttempt(command.sourceId())
                    : sourceOfCase(command.sourceId());
            if (source.error() != null) {
                return failures.failure(source.error());
            }
            if (source.studentId() == command.seniorUserId()) {
                return failures.failure(AssessmentPeerReviewError.CANNOT_REEVALUATE_OWN_WORK);
            }
            if (caseRepository.findOpenByStudentAndNode(source.studentId(), source.pathNodeId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }

            Reevaluation saved = transactions.execute(status -> {
                NodeReopenOutcome outcome = learningPathFacade.reopenNode(source.pathNodeId());
                if (outcome != NodeReopenOutcome.REOPENED) {
                    throw new NodeReopenFailedException(outcome);
                }
                if (command.sourceType() == ReevaluationSourceType.CASE) {
                    reopenCase(command.sourceId());
                }
                return reevaluationRepository.save(new Reevaluation(command.sourceType(), command.sourceId(),
                        source.studentId(), command.seniorUserId(), justification));
            });

            log.info("The senior {} ordered the reevaluation of the {} {} of the student {}",
                    command.seniorUserId(), command.sourceType().value(), command.sourceId(), source.studentId());
            return Result.success(saved);
        } catch (CaseChangedException exception) {
            return failures.failure(AssessmentPeerReviewError.CASE_NOT_APPROVED);
        } catch (NodeReopenFailedException exception) {
            return failures.failure(exception.outcome() == NodeReopenOutcome.FAILED
                    ? AssessmentPeerReviewError.DATABASE_ERROR
                    : AssessmentPeerReviewError.NODE_NOT_REOPENABLE);
        } catch (RuntimeException exception) {
            log.error("Could not order the reevaluation of the {} {}", command.sourceType(), command.sourceId(),
                    exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    private Source sourceOfAttempt(int attemptId) {
        Optional<AssessmentAttempt> attempt = attemptRepository.findById(attemptId);
        if (attempt.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.ATTEMPT_NOT_FOUND);
        }
        Optional<BlueprintView> blueprint = learningPathFacade.getBlueprint(attempt.get().getBlueprintId());
        if (blueprint.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.BLUEPRINT_NOT_FOUND);
        }
        if (!attempt.get().isPassed()) {
            return Source.failed(AssessmentPeerReviewError.ATTEMPT_NOT_PASSED);
        }
        return new Source(attempt.get().getStudentId(), blueprint.get().pathNodeId(), null);
    }

    private Source sourceOfCase(int caseId) {
        Optional<VerificationCase> found = caseRepository.findById(caseId);
        if (found.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.CASE_NOT_FOUND);
        }
        if (!found.get().canBeReevaluated()) {
            return Source.failed(AssessmentPeerReviewError.CASE_NOT_APPROVED);
        }
        return new Source(found.get().getStudentId(), found.get().getPathNodeId(), null);
    }

    /** Reopens the approved case and gives it to another verifier, if any qualifies; otherwise it waits as pending. */
    private void reopenCase(int caseId) {
        // Read again under the lock: the case may have changed since the checks.
        VerificationCase locked = caseRepository.findByIdForUpdate(caseId).orElseThrow();
        if (!locked.canBeReevaluated()) {
            throw new CaseChangedException();
        }
        locked.reopenForReevaluation(deadlineResolver.forStudent(locked.getStudentId()));
        assignmentService.tryAssign(locked);
        caseRepository.save(locked);
    }

    /** What the reevaluation refers to: the student, the node of the path, or why it cannot be used. */
    private record Source(int studentId, int pathNodeId, AssessmentPeerReviewError error) {

        static Source failed(AssessmentPeerReviewError error) {
            return new Source(0, 0, error);
        }
    }

    /** Thrown inside the transaction when Learning Path Engine does not reopen the node, so nothing is saved. */
    private static final class NodeReopenFailedException extends RuntimeException {

        private final transient NodeReopenOutcome outcome;

        NodeReopenFailedException(NodeReopenOutcome outcome) {
            super("The node was not reopened: " + outcome);
            this.outcome = outcome;
        }

        NodeReopenOutcome outcome() {
            return outcome;
        }
    }

    /** Thrown inside the transaction when the case stopped being approved after the checks. */
    private static final class CaseChangedException extends RuntimeException {

        CaseChangedException() {
            super("The case is no longer approved.");
        }
    }
}
