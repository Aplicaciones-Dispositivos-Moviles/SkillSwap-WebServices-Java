package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/**
 * Assessment blueprint resource for the REST API.
 *
 * @param id          the unique identifier of the blueprint
 * @param pathNodeId  the node the assessment belongs to
 * @param skillTag    the skill code being assessed
 * @param skillName   the display name of the skill
 * @param questions   the questions, without their correct answers
 * @param generatedAt when the assessment was generated (UTC)
 */
public record AssessmentBlueprintResource(
        int id,
        int pathNodeId,
        String skillTag,
        String skillName,
        List<QuestionResource> questions,
        Instant generatedAt) {
}
