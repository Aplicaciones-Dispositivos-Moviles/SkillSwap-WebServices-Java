package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.assessmentpeerreview.application.commandservices.ReviewDeadlinePolicyCommandService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.DefineReviewDeadlinesCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReviewDeadlinePolicyRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Review deadline policy command service. Only a Verificador senior (an enabled verifier with the Gold rank and a
 * reliability of 90 or more, as Reputation calculates it) defines the deadlines; both plans are saved together.
 */
@Service
public class ReviewDeadlinePolicyCommandServiceImpl implements ReviewDeadlinePolicyCommandService {

    private static final Logger log = LoggerFactory.getLogger(ReviewDeadlinePolicyCommandServiceImpl.class);

    private final ReviewDeadlinePolicyRepository policyRepository;
    private final VerifierProfileRepository profileRepository;
    private final ReputationContextFacade reputationFacade;
    private final TransactionOperations transactions;
    private final AssessmentPeerReviewFailures failures;

    public ReviewDeadlinePolicyCommandServiceImpl(ReviewDeadlinePolicyRepository policyRepository,
                                                  VerifierProfileRepository profileRepository,
                                                  ReputationContextFacade reputationFacade,
                                                  TransactionOperations transactions,
                                                  MessageSource messageSource) {
        this.policyRepository = policyRepository;
        this.profileRepository = profileRepository;
        this.reputationFacade = reputationFacade;
        this.transactions = transactions;
        this.failures = new AssessmentPeerReviewFailures(messageSource);
    }

    @Override
    public Result<List<ReviewDeadlinePolicy>> handle(DefineReviewDeadlinesCommand command) {
        try {
            boolean enabledVerifier = profileRepository.findByUserId(command.seniorUserId())
                    .map(VerifierProfile::isVerified)
                    .orElse(false);
            if (!enabledVerifier || !reputationFacade.isSeniorVerifier(command.seniorUserId())) {
                return failures.failure(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
            }

            if (command.premiumPlanHours() == null || command.freePlanBusinessDays() == null
                    || command.premiumPlanHours() <= 0 || command.freePlanBusinessDays() <= 0) {
                return failures.failure(AssessmentPeerReviewError.INVALID_REVIEW_DEADLINE);
            }
            ReviewDeadline premium = ReviewDeadline.hours(command.premiumPlanHours());
            ReviewDeadline free = ReviewDeadline.businessDays(command.freePlanBusinessDays());
            if (!ReviewDeadlinePolicy.isValidFor(ReviewDeadlinePolicy.PREMIUM_PLAN, premium)
                    || !ReviewDeadlinePolicy.isValidFor(ReviewDeadlinePolicy.FREE_PLAN, free)) {
                return failures.failure(AssessmentPeerReviewError.INVALID_REVIEW_DEADLINE);
            }

            List<ReviewDeadlinePolicy> saved = transactions.execute(status -> List.of(
                    define(ReviewDeadlinePolicy.PREMIUM_PLAN, premium, command.seniorUserId()),
                    define(ReviewDeadlinePolicy.FREE_PLAN, free, command.seniorUserId())));
            log.info("The senior {} set the review deadlines: {} hours (monthly), {} business days (free)",
                    command.seniorUserId(), premium.amount(), free.amount());
            return Result.success(saved);
        } catch (RuntimeException exception) {
            log.error("Could not define the review deadlines", exception);
            return failures.failure(AssessmentPeerReviewFailures.toError(exception));
        }
    }

    private ReviewDeadlinePolicy define(String plan, ReviewDeadline deadline, int seniorUserId) {
        ReviewDeadlinePolicy policy = policyRepository.findByPlan(plan)
                .map(existing -> existing.define(deadline, seniorUserId))
                .orElseGet(() -> new ReviewDeadlinePolicy(plan, deadline, seniorUserId));
        return policyRepository.save(policy);
    }
}
