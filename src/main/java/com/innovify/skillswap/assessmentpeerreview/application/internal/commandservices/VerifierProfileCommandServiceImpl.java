package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.VerifierProfileCommandService;
import com.innovify.skillswap.assessmentpeerreview.application.internal.CaseAssignmentService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Verifier profile command service. Not {@code @Transactional}: saving the profile and giving it the pending
 * cases it can take is one unit, run in the given {@link TransactionOperations}.
 */
public class VerifierProfileCommandServiceImpl implements VerifierProfileCommandService {

    private static final Logger log = LoggerFactory.getLogger(VerifierProfileCommandServiceImpl.class);

    private final VerifierProfileRepository profileRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final CaseAssignmentService caseAssignmentService;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public VerifierProfileCommandServiceImpl(VerifierProfileRepository profileRepository,
                                             LearningPathContextFacade learningPathFacade,
                                             CaseAssignmentService caseAssignmentService,
                                             TransactionOperations transactions,
                                             MessageSource messageSource) {
        this.profileRepository = profileRepository;
        this.learningPathFacade = learningPathFacade;
        this.caseAssignmentService = caseAssignmentService;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<VerifierProfile> handle(CreateVerifierProfileCommand command) {
        String skillTag = command.skillTag() == null ? "" : command.skillTag().strip();
        if (skillTag.isEmpty()) {
            return failures.failure(AssessmentPeerReviewError.INVALID_SKILL_TAG);
        }

        try {
            // The student can only review the skills they demonstrated themselves.
            if (!learningPathFacade.hasCompletedSkill(command.userId(), skillTag)) {
                return failures.failure(AssessmentPeerReviewError.SKILL_NOT_COMPLETED);
            }

            Optional<VerifierProfile> existing = profileRepository.findByUserId(command.userId());
            if (existing.isPresent() && !existing.get().isVerified()) {
                return failures.failure(AssessmentPeerReviewError.NOT_A_VERIFIER);
            }
            if (existing.isPresent() && !existing.get().addSkill(skillTag)) {
                return failures.failure(AssessmentPeerReviewError.VERIFIER_SKILL_ALREADY_ENABLED);
            }

            VerifierProfile profile = existing.orElseGet(() -> new VerifierProfile(command.userId(), skillTag));

            VerifierProfile saved = transactions.execute(status -> {
                VerifierProfile stored = profileRepository.save(profile);
                caseAssignmentService.assignPending(List.of(skillTag));
                return stored;
            });

            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not create the verifier profile of user {}", command.userId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    @Override
    public Result<VerifierProfile> handle(UpdateVerifierAvailabilityCommand command) {
        try {
            Optional<VerifierProfile> found = profileRepository.findByUserId(command.userId());
            if (found.isEmpty() || !found.get().isVerified()) {
                return failures.failure(AssessmentPeerReviewError.NOT_A_VERIFIER);
            }
            VerifierProfile profile = found.get().setAvailability(command.available());

            VerifierProfile saved = transactions.execute(status -> {
                VerifierProfile stored = profileRepository.save(profile);

                // Turning availability on puts the verifier back in the queue; cases already assigned stay.
                if (command.available()) {
                    caseAssignmentService.assignPending(stored.getSkillTags());
                }
                return stored;
            });

            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not update the availability of user {}", command.userId(), exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }
}
