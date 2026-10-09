package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import java.util.Optional;

/** Assessment attempt query service interface. */
public interface AssessmentAttemptQueryService {

    Optional<AssessmentAttempt> handle(GetAssessmentAttemptByIdQuery query);
}
