package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.shared.application.Result;

/** Assessment blueprint command service interface. */
public interface AssessmentBlueprintCommandService {

    /** Generates the assessment of an available node and points the node to it. */
    Result<AssessmentBlueprint> handle(GenerateAssessmentBlueprintCommand command);
}
