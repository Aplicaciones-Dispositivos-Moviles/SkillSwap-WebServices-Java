package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.AssessmentAttemptCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.EscalationLimitReached;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitAssessmentAttemptOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.AssessmentAttemptPassed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.events.DomainEventPublisher;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Assessment attempt command service.
 *
 * <p>Like the other command services it is not {@code @Transactional}: the checks run outside of any
 * transaction, and only the final step, which must save the attempt together with the node completion or the
 * case it opens, runs in the given {@link TransactionOperations}. Events are published once it is committed.
 *
 * <p>Opening a case uses one of the escalations of the month that the plan of the student allows. The escalations
 * of a student are locked during that transaction, so two failed attempts cannot both take the last one.
 */
@Service
public class AssessmentAttemptCommandServiceImpl implements AssessmentAttemptCommandService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentAttemptCommandServiceImpl.class);

    private final AssessmentAttemptRepository attemptRepository;
    private final VerificationCaseRepository caseRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final CaseAssignmentService caseAssignmentService;
    private final SubscriptionContextFacade subscriptionFacade;
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public AssessmentAttemptCommandServiceImpl(AssessmentAttemptRepository attemptRepository,
                                               VerificationCaseRepository caseRepository,
                                               LearningPathContextFacade learningPathFacade,
                                               CaseAssignmentService caseAssignmentService,
                                               SubscriptionContextFacade subscriptionFacade,
                                               DomainEventPublisher eventPublisher,
                                               TransactionOperations transactions,
                                               MessageSource messageSource) {
        this.attemptRepository = attemptRepository;
        this.caseRepository = caseRepository;
        this.learningPathFacade = learningPathFacade;
        this.caseAssignmentService = caseAssignmentService;
        this.subscriptionFacade = subscriptionFacade;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<SubmitAssessmentAttemptOutcome> handle(SubmitAssessmentAttemptCommand command) {
        try {
            Optional<BlueprintView> found = learningPathFacade.getBlueprint(command.blueprintId());
            if (found.isEmpty()) {
                return failures.failure(AssessmentPeerReviewError.BLUEPRINT_NOT_FOUND);
            }
            BlueprintView blueprint = found.get();

            // Only the owner of the path answers the assessment.
            if (blueprint.studentId() != command.studentId()) {
                return failures.failure(AssessmentPeerReviewError.NOT_BLUEPRINT_OWNER);
            }
            if (!areValidAnswers(command.selectedAnswers(), blueprint.questions().size())) {
                return failures.failure(AssessmentPeerReviewError.INVALID_ANSWERS);
            }
            if (!blueprint.nodeIsAvailable()) {
                return failures.failure(AssessmentPeerReviewError.NODE_NOT_AVAILABLE);
            }

            if (caseRepository.findOpenByStudentAndNode(command.studentId(), blueprint.pathNodeId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }
            if (!blueprint.isLatest()) {
                return failures.failure(AssessmentPeerReviewError.BLUEPRINT_OUTDATED);
            }
            if (attemptRepository.findByBlueprintId(blueprint.blueprintId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.ATTEMPT_ALREADY_SUBMITTED);
            }

            List<Integer> correctAnswers = blueprint.questions().stream()
                    .map(BlueprintQuestionView::correctAnswer)
                    .toList();
            AssessmentAttempt graded = new AssessmentAttempt(blueprint.blueprintId(), command.studentId(),
                    command.selectedAnswers(), correctAnswers);

            SubmitAssessmentAttemptOutcome outcome = transactions.execute(status -> {
                AssessmentAttempt attempt = attemptRepository.save(graded);

                if (attempt.isPassed()) {
                    NodeCompletionOutcome completion = learningPathFacade.completeNode(blueprint.pathNodeId());
                    if (completion != NodeCompletionOutcome.COMPLETED) {
                        throw new NodeCompletionFailedException(completion);
                    }
                    return new SubmitAssessmentAttemptOutcome(attempt, null);
                }

                caseRepository.lockStudentEscalations(command.studentId());
                PlanLimitsView limits = subscriptionFacade.getPlanLimits(command.studentId());
                int used = caseRepository.countOpenedByStudentSince(command.studentId(),
                        EscalationCalendar.startOfMonth(Instant.now()));
                if (used >= limits.monthlyEscalations()) {
                    // The attempt stays recorded; the student can try the node again or change their plan.
                    return new SubmitAssessmentAttemptOutcome(attempt, null,
                            new EscalationLimitReached(limits.plan(), limits.monthlyEscalations(), used));
                }

                // The attempts are the answers of the quiz of the node: the only work that opens a case today.
                VerificationCase opened = new VerificationCase(attempt.getId(), command.studentId(),
                        blueprint.pathNodeId(), blueprint.skillTag(), CaseType.QUIZ, toReviewDeadline(limits));
                caseAssignmentService.tryAssign(opened);
                return new SubmitAssessmentAttemptOutcome(attempt, caseRepository.save(opened));
            });

            if (outcome.attempt().isPassed()) {
                eventPublisher.publish(new AssessmentAttemptPassed(outcome.attempt().getId(),
                        outcome.attempt().getStudentId(), blueprint.pathNodeId(), blueprint.skillTag()));
            }

            return Result.success(outcome);
        } catch (NodeCompletionFailedException exception) {
            return failures.failure(AssessmentPeerReviewFailures.fromNodeCompletion(exception.outcome()));
        } catch (RuntimeException exception) {
            log.error("Could not submit the attempt of student {} for blueprint {}", command.studentId(),
                    command.blueprintId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /** The deadline of the plan, as this context sees it. */
    private static ReviewDeadline toReviewDeadline(PlanLimitsView limits) {
        return limits.reviewDeadlineHours() != null
                ? ReviewDeadline.hours(limits.reviewDeadlineHours())
                : ReviewDeadline.businessDays(limits.reviewDeadlineBusinessDays());
    }

    private static boolean areValidAnswers(List<Integer> answers, int questionCount) {
        return answers != null
                && answers.size() == questionCount
                && answers.stream().allMatch(answer -> answer != null && answer >= 0
                        && answer < AssessmentAttempt.ANSWER_OPTION_COUNT);
    }
}
