package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import com.innovify.skillswap.learningpathengine.application.queryservices.AssessmentBlueprintQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetAssessmentBlueprintByPathNodeIdQuery;
import com.innovify.skillswap.learningpathengine.domain.repositories.AssessmentBlueprintRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AssessmentBlueprintQueryServiceImpl implements AssessmentBlueprintQueryService {

    private final AssessmentBlueprintRepository blueprintRepository;

    public AssessmentBlueprintQueryServiceImpl(AssessmentBlueprintRepository blueprintRepository) {
        this.blueprintRepository = blueprintRepository;
    }

    @Override
    public Optional<AssessmentBlueprint> handle(GetAssessmentBlueprintByPathNodeIdQuery query) {
        return blueprintRepository.findLatestByPathNodeId(query.pathNodeId());
    }
}
