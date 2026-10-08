package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AssessmentAttemptQueryServiceImpl implements AssessmentAttemptQueryService {

    private final AssessmentAttemptRepository attemptRepository;

    public AssessmentAttemptQueryServiceImpl(AssessmentAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    public Optional<AssessmentAttempt> handle(GetAssessmentAttemptByIdQuery query) {
        return attemptRepository.findById(query.attemptId());
    }
}
