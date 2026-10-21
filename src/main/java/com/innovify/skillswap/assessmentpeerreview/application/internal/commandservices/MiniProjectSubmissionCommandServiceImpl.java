package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.EscalationLimitReached;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.commandservices.SubmitMiniProjectOutcome;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.ReviewDeadlineResolver;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.services.EscalationCalendar;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectView;
import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
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
 * Mini-project submission command service. Like the other command services it is not {@code @Transactional}: the checks
 * run first and the writes go in one explicit transaction. The delivery and its verification case are saved together
 * (the case is assigned to a verifier when one is available), so a delivery never exists without its case unless the
 * monthly escalations of the plan were used up. The event is published once the transaction is committed.
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
    private final DomainEventPublisher eventPublisher;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public MiniProjectSubmissionCommandServiceImpl(MiniProjectSubmissionRepository submissionRepository,
                                                   VerificationCaseRepository caseRepository,
                                                   LearningPathContextFacade learningPathFacade,
                                                   CaseAssignmentService caseAssignmentService,
                                                   SubscriptionContextFacade subscriptionFacade,
                                                   ReviewDeadlineResolver deadlineResolver,
                                                   DomainEventPublisher eventPublisher,
                                                   TransactionOperations transactions,
                                                   MessageSource messageSource) {
        this.submissionRepository = submissionRepository;
        this.caseRepository = caseRepository;
        this.learningPathFacade = learningPathFacade;
        this.caseAssignmentService = caseAssignmentService;
        this.subscriptionFacade = subscriptionFacade;
        this.deadlineResolver = deadlineResolver;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<SubmitMiniProjectOutcome> handle(SubmitMiniProjectCommand command) {
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
            if (submissionRepository.findByMiniProjectId(miniProject.miniProjectId()).isPresent()) {
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
            }

            SubmitMiniProjectOutcome outcome;
            try {
                outcome = transactions.execute(status -> saveWithItsCase(submission));
            } catch (DataIntegrityViolationException exception) {
                // Two deliveries at once: the unique constraints let only one through. The re-query tells which one.
                if (submissionRepository.findByMiniProjectId(miniProject.miniProjectId()).isPresent()) {
                    return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
                }
                return failures.failure(AssessmentPeerReviewError.OPEN_CASE_ALREADY_EXISTS);
            }

            MiniProjectSubmission saved = outcome.submission();
            eventPublisher.publish(new MiniProjectSubmitted(saved.getId(), saved.getMiniProjectId(),
                    saved.getStudentId(), saved.getPathNodeId(), saved.getSkillTag(), saved.getRepositoryUrl(),
                    saved.getSubmittedAt()));
            return Result.success(outcome);
        } catch (RuntimeException exception) {
            log.error("Could not register the delivery of the mini-project {}", command.miniProjectId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    /** Inside the transaction: saves the delivery and opens its case, unless the plan has no escalations left. */
    private SubmitMiniProjectOutcome saveWithItsCase(MiniProjectSubmission submission) {
        MiniProjectSubmission saved = submissionRepository.save(submission);

        // Serializes the escalations of the student, so two concurrent deliveries cannot both take the last one.
        caseRepository.lockStudentEscalations(saved.getStudentId());
        PlanLimitsView limits = subscriptionFacade.getPlanLimits(saved.getStudentId());
        int used = caseRepository.countOpenedByStudentSince(saved.getStudentId(),
                EscalationCalendar.startOfMonth(Instant.now()));
        if (used >= limits.monthlyEscalations()) {
            return new SubmitMiniProjectOutcome(saved, null,
                    new EscalationLimitReached(limits.plan(), limits.monthlyEscalations(), used));
        }

        VerificationCase opened = VerificationCase.forMiniProject(saved.getId(), saved.getStudentId(),
                saved.getPathNodeId(), saved.getSkillTag(), saved.getRepositoryUrl(), deadlineResolver.forPlan(limits));
        caseAssignmentService.tryAssign(opened);
        return new SubmitMiniProjectOutcome(saved, caseRepository.save(opened), null);
    }
}
