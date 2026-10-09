package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.ReviewDeadlinePolicy;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.DefineReviewDeadlinesCommand;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;

/** Review deadline policy command service interface. */
public interface ReviewDeadlinePolicyCommandService {

    /**
     * A Verificador senior defines the deadline of both plans (US39): up to 48 hours for the monthly plan and up to 5
     * business days for the free plan. They apply to the cases opened from then on. Fails with NOT_SENIOR_VERIFIER or
     * INVALID_REVIEW_DEADLINE. @return the policies of the monthly and the free plan, in that order
     */
    Result<List<ReviewDeadlinePolicy>> handle(DefineReviewDeadlinesCommand command);
}
