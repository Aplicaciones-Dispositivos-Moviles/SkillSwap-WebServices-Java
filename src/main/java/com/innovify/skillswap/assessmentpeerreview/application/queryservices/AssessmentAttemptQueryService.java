package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptDetailQuery;
import java.util.Optional;

/** Assessment attempt query service interface. */
public interface AssessmentAttemptQueryService {

    Optional<AssessmentAttempt> handle(GetAssessmentAttemptByIdQuery query);

    /** The attempt with the sub-topic to reinforce. */
    Optional<AssessmentAttemptDetail> handle(GetAssessmentAttemptDetailQuery query);
}
