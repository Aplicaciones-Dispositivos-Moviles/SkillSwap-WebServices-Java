package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.MiniProjectSubmissionCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.MiniProjectSubmitted;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.MiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectView;
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

/**
 * Mini-project submission command service. Like the other command services it is not {@code @Transactional}: the checks
 * run first and the only write is a single save. The event is published once the delivery is saved.
 *
 * <p>This service only receives the delivery. The preliminary AI evaluation and the verification case react to the
 * {@code MiniProjectSubmitted} event, so a delivery does not open a case here.
 */
@Service
public class MiniProjectSubmissionCommandServiceImpl implements MiniProjectSubmissionCommandService {

    private static final Logger log = LoggerFactory.getLogger(MiniProjectSubmissionCommandServiceImpl.class);

    private final MiniProjectSubmissionRepository submissionRepository;
    private final VerificationCaseRepository caseRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final DomainEventPublisher eventPublisher;
    private final AssessmentPeerReviewFailures failures;

    public MiniProjectSubmissionCommandServiceImpl(MiniProjectSubmissionRepository submissionRepository,
                                                   VerificationCaseRepository caseRepository,
                                                   LearningPathContextFacade learningPathFacade,
                                                   DomainEventPublisher eventPublisher,
                                                   MessageSource messageSource) {
        this.submissionRepository = submissionRepository;
        this.caseRepository = caseRepository;
        this.learningPathFacade = learningPathFacade;
        this.eventPublisher = eventPublisher;
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

            MiniProjectSubmission saved;
            try {
                saved = submissionRepository.save(submission);
            } catch (DataIntegrityViolationException exception) {
                // Two deliveries of the same mini-project at once: the unique constraint lets only one through.
                return failures.failure(AssessmentPeerReviewError.MINI_PROJECT_ALREADY_SUBMITTED);
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
}
