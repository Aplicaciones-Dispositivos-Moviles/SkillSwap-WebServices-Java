package com.innovify.skillswap.learningpathengine.application.queryservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetAssessmentBlueprintByPathNodeIdQuery;
import java.util.Optional;

/** Assessment blueprint query service interface. */
public interface AssessmentBlueprintQueryService {

    /** The latest blueprint generated for the node. */
    Optional<AssessmentBlueprint> handle(GetAssessmentBlueprintByPathNodeIdQuery query);
}
