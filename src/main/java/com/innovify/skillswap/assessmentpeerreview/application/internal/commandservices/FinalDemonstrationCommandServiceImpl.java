package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.FinalDemonstrationCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.DemonstrationAssignment;
import com.innovify.skillswap.assessmentpeerreview.application.internal.DemonstrationAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CancelFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.RescheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ScheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationCancelled;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationScheduled;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.PathCompletionView;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Final demonstration command service. Like the other command services it is not {@code @Transactional}: the
 * checks run first and the writes go in one explicit transaction.
 *
 * <p>The path must be completed and belong to the student, the moment must be at least 24 hours ahead and the path
 * cannot have another demonstration that still counts. The verifier is chosen among the available ones enabled in a
 * skill of the path, with the least work. Changing a demonstration reads it with a lock inside the transaction.
 */
@Service
public class FinalDemonstrationCommandServiceImpl implements FinalDemonstrationCommandService {

    private static final Logger log = LoggerFactory.getLogger(FinalDemonstrationCommandServiceImpl.class);

    private final FinalDemonstrationRepository demonstrationRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final DemonstrationAssignmentService assignmentService;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public FinalDemonstrationCommandServiceImpl(FinalDemonstrationRepository demonstrationRepository,
                                                LearningPathContextFacade learningPathFacade,
                                                DemonstrationAssignmentService assignmentService,
                                                DomainEventPublisher eventPublisher,
                                                TransactionOperations transactions,
                                                MessageSource messageSource) {
        this.demonstrationRepository = demonstrationRepository;
        this.learningPathFacade = learningPathFacade;
        this.assignmentService = assignmentService;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<FinalDemonstration> handle(ScheduleFinalDemonstrationCommand command) {
        try {
            Optional<PathCompletionView> found = learningPathFacade.getPathCompletion(command.learningPathId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.LEARNING_PATH_NOT_FOUND);
            }
            PathCompletionView path = found.get();
            if (path.studentId() != command.studentId()) {
                return failures.failure(AssessmentPeerReviewError.NOT_PATH_OWNER);
            }
            if (command.modality() == null) {
                return failures.failure(AssessmentPeerReviewError.INVALID_MODALITY);
            }
            if (command.scheduledAt() == null) {
                return failures.failure(AssessmentPeerReviewError.INVALID_SCHEDULE);
            }
            if (!path.completed()) {
                return failures.failure(AssessmentPeerReviewError.PATH_NOT_COMPLETED);
            }
            if (!FinalDemonstration.isFarEnough(command.scheduledAt(), Instant.now())) {
                return failures.failure(AssessmentPeerReviewError.SCHEDULE_TOO_SOON);
            }
            if (demonstrationRepository.findBlockingByLearningPathId(path.pathId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_ALREADY_ACTIVE);
            }

            Optional<DemonstrationAssignment> assignment = assignmentService.findVerifier(command.studentId(),
                    path.skillTags());
            if (assignment.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.NO_VERIFIER_AVAILABLE);
            }

            FinalDemonstration demonstration = new FinalDemonstration(command.studentId(), path.pathId(),
                    assignment.get().skillTag(), assignment.get().verifierUserId(), command.modality(),
                    command.scheduledAt());
            FinalDemonstration saved;
            try {
                saved = transactions.execute(status -> demonstrationRepository.save(demonstration));
            } catch (DataIntegrityViolationException exception) {
                // Two requests at once: the unique index lets only one demonstration per path through.
                if (demonstrationRepository.findBlockingByLearningPathId(path.pathId()).isPresent()) {
                    return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_ALREADY_ACTIVE);
                }
                throw exception;
            }

            eventPublisher.publish(new FinalDemonstrationScheduled(saved.getId(), saved.getStudentId(),
                    saved.getVerifierUserId(), saved.getSkillTag(), saved.getScheduledAt()));
            return Result.success(saved);
        } catch (DomainException exception) {
            return failures.failure(AssessmentPeerReviewError.INVALID_SCHEDULE);
        } catch (RuntimeException exception) {
            log.error("The final demonstration of the path {} could not be scheduled", command.learningPathId(),
                    exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<FinalDemonstration> handle(RescheduleFinalDemonstrationCommand command) {
        try {
            if (command.scheduledAt() == null) {
                return failures.failure(AssessmentPeerReviewError.INVALID_SCHEDULE);
            }
            if (!FinalDemonstration.isFarEnough(command.scheduledAt(), Instant.now())) {
                return failures.failure(AssessmentPeerReviewError.SCHEDULE_TOO_SOON);
            }

            return change(command.demonstrationId(), command.studentId(),
                    demonstration -> demonstration.reschedule(command.scheduledAt()), false);
        } catch (RuntimeException exception) {
            log.error("The final demonstration {} could not be rescheduled", command.demonstrationId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<FinalDemonstration> handle(CancelFinalDemonstrationCommand command) {
        try {
            return change(command.demonstrationId(), command.studentId(), FinalDemonstration::cancel, true);
        } catch (RuntimeException exception) {
            log.error("The final demonstration {} could not be cancelled", command.demonstrationId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /** Applies a change to a scheduled demonstration of the student, read with a lock; publishes the cancellation. */
    private Result<FinalDemonstration> change(int demonstrationId, int studentId,
                                              java.util.function.Consumer<FinalDemonstration> change,
                                              boolean cancellation) {
        if (demonstrationRepository.findById(demonstrationId).isEmpty()) {
            return failures.failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
        }

        Result<FinalDemonstration> result = transactions.execute(status -> {
            // Read again under the lock: it may have been cancelled meanwhile.
            Optional<FinalDemonstration> locked = demonstrationRepository.findByIdForUpdate(demonstrationId);
            if (locked.isEmpty()) {
                return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
            }
            FinalDemonstration demonstration = locked.get();
            if (!demonstration.isOwnedBy(studentId)) {
                return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.NOT_DEMONSTRATION_OWNER);
            }
            if (!demonstration.isScheduled()) {
                return failures.<FinalDemonstration>failure(AssessmentPeerReviewError.DEMONSTRATION_NOT_SCHEDULED);
            }
            change.accept(demonstration);
            return Result.success(demonstrationRepository.save(demonstration));
        });

        if (cancellation && result.isSuccess()) {
            FinalDemonstration saved = result.value();
            eventPublisher.publish(new FinalDemonstrationCancelled(saved.getId(), saved.getStudentId(),
                    saved.getVerifierUserId()));
        }
        return result;
    }
}
