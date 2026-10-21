package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.EscalationLimitReached;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectEvaluationOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.EvaluateMiniProjectSubmissionCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.MiniProjectBrief;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectRubricView;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectView;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Mini-project submission command service. Like the other command services it is not {@code @Transactional}: the
 * checks run first and the writes go in one explicit transaction.
 *
 * <p>The delivery is only received here: it is saved as Pending and {@code MiniProjectSubmitted} is published once it
 * is committed. Its preliminary evaluation reacts to that event (US19): the AI is asked outside any transaction, since
 * it may take up to a minute, and then, in one transaction with the delivery locked, the delivery is approved with its
 * node completed, or the verification case is opened like the one of a failed quiz (deadline of the plan, assignment
 * to an available verifier and one of the monthly escalations of the plan, with the escalations of the student
 * locked).
 */
@Service
public class MiniProjectSubmissionCommandServiceImpl implements MiniProjectSubmissionCommandService {

    private static final Logger log = LoggerFactory.getLogger(MiniProjectSubmissionCommandServiceImpl.class);

    private final MiniProjectSubmissionRepository submissionRepository;
    private final VerificationCaseRepository caseRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final CaseAssignmentService caseAssignmentService;
    private final SubscriptionContextFacade subscriptionFacade;
    private final ReviewDeadlineResolver deadlineResolver;
    private final MiniProjectEvaluationService evaluationService;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public MiniProjectSubmissionCommandServiceImpl(MiniProjectSubmissionRepository submissionRepository,
                                                   VerificationCaseRepository caseRepository,
                                                   LearningPathContextFacade learningPathFacade,
                                                   CaseAssignmentService caseAssignmentService,
                                                   SubscriptionContextFacade subscriptionFacade,
                                                   ReviewDeadlineResolver deadlineResolver,
                                                   MiniProjectEvaluationService evaluationService,
                                                   DomainEventPublisher eventPublisher,
                                                   TransactionOperations transactions,
                                                   MessageSource messageSource) {
        this.submissionRepository = submissionRepository;
        this.caseRepository = caseRepository;
        this.learningPathFacade = learningPathFacade;
        this.caseAssignmentService = caseAssignmentService;
        this.subscriptionFacade = subscriptionFacade;
        this.deadlineResolver = deadlineResolver;
        this.evaluationService = evaluationService;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<MiniProjectSubmission> handle(SubmitMiniProjectCommand command) {
        try {
            Optional<MiniProjectView> found = learningPathFacade.getMiniProject(command.miniProjectId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_NOT_FOUND);
            }
            MiniProjectView miniProject = found.get();

            // Only the owner of the path delivers its mini-projects.
            if (miniProject.studentId() != command.studentId()) {
                return failures.failure(AssessmentPeerReviewError.NOT_MINI_PROJECT_OWNER);
            }

            // The rest of the data comes from the mini-project, so only the link can make the delivery invalid.
            MiniProjectSubmission submission;
            try {
                submission = new MiniProjectSubmission(miniProject.miniProjectId(), miniProject.studentId(),
                        miniProject.pathNodeId(), miniProject.skillTag(), command.repositoryUrl());
            } catch (DomainException exception) {
                return failures.failure(AssessmentPeerReviewError.INVALID_REPOSITORY_URL);
            }

            // Checked first: once delivered, its evaluation may have completed the node or opened its case, and the
            // student must hear that this mini-project was already delivered, not one of those consequences.
            if (submissionRepository.findByMiniProjectId(miniProject.miniProjectId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
            }
            if (!miniProject.nodeIsAvailable()) {
                return failures.failure(AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
            }
            if (!miniProject.isLatest()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_OUTDATED);
            }
            if (Instant.now().isAfter(miniProject.dueAt())) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_EXPIRED);
            }
            if (caseRepository.findOpenByStudentAndNode(command.studentId(), miniProject.pathNodeId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }

            MiniProjectSubmission saved;
            try {
                saved = transactions.execute(status -> submissionRepository.save(submission));
            } catch (DataIntegrityViolationException exception) {
                // Two deliveries at once: the unique constraint lets only one through.
                if (submissionRepository.findByMiniProjectId(miniProject.miniProjectId()).isPresent()) {
                    return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
                }
                throw exception;
            }

            eventPublisher.publish(new MiniProjectSubmitted(saved.getId(), saved.getMiniProjectId(),
                    saved.getStudentId(), saved.getPathNodeId(), saved.getSkillTag(), saved.getRepositoryUrl(),
                    saved.getSubmittedAt()));
            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not register the delivery of the mini-project {}", command.miniProjectId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<MiniProjectEvaluationOutcome> handle(EvaluateMiniProjectSubmissionCommand command) {
        try {
            Optional<MiniProjectSubmission> found = submissionRepository.findById(command.submissionId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_SUBMISSION_NOT_FOUND);
            }
            MiniProjectSubmission submission = found.get();
            if (!submission.isAwaitingEvaluation()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_EVALUATED);
            }
            // The student failed the quiz of the node after delivering: that case decides the node, and a node has
            // one open case at a time, so the AI is not even asked.
            if (caseRepository.findOpenByStudentAndNode(submission.getStudentId(),
                    submission.getPathNodeId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }

            Optional<MiniProjectRubricView> rubric =
                    learningPathFacade.getMiniProjectRubric(submission.getMiniProjectId());
            PreliminaryEvaluation evaluation = rubric.map(view -> evaluate(submission, view)).orElse(null);
            boolean sufficient = evaluation != null && evaluation.isSufficientFor(rubric.get().passingScore());

            MiniProjectEvaluationOutcome outcome = transactions.execute(status ->
                    settle(submission.getId(), evaluation, sufficient ? rubric.get().passingScore() : null));
            log.info("Preliminary evaluation of the delivery {}: {} (score {}, confidence {})", submission.getId(),
                    outcome.submission().getEvaluationStatus().value(), outcome.submission().getPreliminaryScore(),
                    outcome.submission().getPreliminaryConfidence());
            return Result.success(outcome);
        } catch (AlreadyEvaluatedException exception) {
            return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_EVALUATED);
        } catch (NodeCompletionFailedException exception) {
            return failures.failure(AssessmentPeerReviewFailures.fromNodeCompletion(exception.outcome()));
        } catch (RuntimeException exception) {
            log.error("Could not record the preliminary evaluation of the delivery {}", command.submissionId(),
                    exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /**
     * Asks the AI. When it cannot evaluate the delivery the answer is null, which counts as a low confidence: a
     * verifier reviews it (US19 escenario 3).
     */
    private PreliminaryEvaluation evaluate(MiniProjectSubmission submission, MiniProjectRubricView rubric) {
        MiniProjectBrief brief = new MiniProjectBrief(rubric.skillTag(), rubric.title(), rubric.statement(),
                rubric.rubric().stream()
                        .map(criterion -> new MiniProjectBrief.Criterion(criterion.name(), criterion.description(),
                                criterion.weight()))
                        .toList(),
                rubric.passingScore());
        try {
            return evaluationService.evaluate(brief, submission.getRepositoryUrl());
        } catch (RuntimeException exception) {
            log.warn("The AI could not evaluate the delivery {}; it goes to a verifier", submission.getId(),
                    exception);
            return null;
        }
    }

    /**
     * Inside the transaction, with the delivery locked so it is settled once: approves it and completes its node, or
     * opens its case unless the plan has no escalations left.
     *
     * @param passingScore the passing score of the rubric when the evaluation reaches it; null when a verifier must
     *                     review the delivery
     */
    private MiniProjectEvaluationOutcome settle(int submissionId, PreliminaryEvaluation evaluation,
                                                Integer passingScore) {
        MiniProjectSubmission submission = submissionRepository.findByIdForUpdate(submissionId).orElseThrow();
        if (!submission.isAwaitingEvaluation()) {
            throw new AlreadyEvaluatedException();
        }

        if (passingScore != null) {
            // A node that is already completed is not a problem: the student got there anyway.
            NodeCompletionOutcome completion = learningPathFacade.completeNode(submission.getPathNodeId());
            if (completion != NodeCompletionOutcome.COMPLETED
                    && completion != NodeCompletionOutcome.ALREADY_COMPLETED) {
                throw new NodeCompletionFailedException(completion);
            }
            return new MiniProjectEvaluationOutcome(
                    submissionRepository.save(submission.approve(evaluation, passingScore)), null, null);
        }

        // Serializes the escalations of the student, so a failed quiz and a delivery cannot both take the last one.
        caseRepository.lockStudentEscalations(submission.getStudentId());
        PlanLimitsView limits = subscriptionFacade.getPlanLimits(submission.getStudentId());
        int used = caseRepository.countOpenedByStudentSince(submission.getStudentId(),
                EscalationCalendar.startOfMonth(Instant.now()));
        if (used >= limits.monthlyEscalations()) {
            return new MiniProjectEvaluationOutcome(submissionRepository.save(submission.keepWithoutReview(evaluation)),
                    null, new EscalationLimitReached(limits.plan(), limits.monthlyEscalations(), used));
        }

        VerificationCase opened = VerificationCase.forMiniProject(submission.getId(), submission.getStudentId(),
                submission.getPathNodeId(), submission.getSkillTag(), submission.getRepositoryUrl(),
                deadlineResolver.forPlan(limits));
        caseAssignmentService.tryAssign(opened);
        VerificationCase saved = caseRepository.save(opened);
        return new MiniProjectEvaluationOutcome(submissionRepository.save(submission.escalate(evaluation)), saved,
                null);
    }

    /** Thrown inside the transaction when another evaluation settled the delivery first, so nothing is saved. */
    private static final class AlreadyEvaluatedException extends RuntimeException {

        AlreadyEvaluatedException() {
            super("The delivery was already evaluated.");
        }
    }
}
