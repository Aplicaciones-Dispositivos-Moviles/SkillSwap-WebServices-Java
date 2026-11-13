package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.internal.WeakSubTopicResolver;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptDetail;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.AssessmentAttemptQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetAssessmentAttemptDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AssessmentAttemptQueryServiceImpl implements AssessmentAttemptQueryService {

    private final AssessmentAttemptRepository attemptRepository;
    private final LearningPathContextFacade learningPathFacade;

    public AssessmentAttemptQueryServiceImpl(AssessmentAttemptRepository attemptRepository,
                                             LearningPathContextFacade learningPathFacade) {
        this.attemptRepository = attemptRepository;
        this.learningPathFacade = learningPathFacade;
    }

    @Override
    public Optional<AssessmentAttempt> handle(GetAssessmentAttemptByIdQuery query) {
        return attemptRepository.findById(query.attemptId());
    }

    @Override
    public Optional<AssessmentAttemptDetail> handle(GetAssessmentAttemptDetailQuery query) {
        Optional<AssessmentAttempt> attempt = attemptRepository.findById(query.attemptId());
        if (attempt.isEmpty()) {
            return Optional.empty();
        }

        // Without its blueprint there are no sub-topics to look at, but the attempt can still be read.
        Optional<BlueprintView> blueprint = learningPathFacade.getBlueprint(attempt.get().getBlueprintId());
        String weakSubTopic = blueprint
                .map(view -> WeakSubTopicResolver.resolve(attempt.get(), view.questions()))
                .orElse(null);
        return Optional.of(new AssessmentAttemptDetail(attempt.get(), weakSubTopic));
    }
}
