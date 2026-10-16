package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

/**
 * Mini-project resource for the REST API.
 *
 * @param id           the unique identifier of the mini-project
 * @param pathNodeId   the node it assesses
 * @param skillTag     the skill code being demonstrated
 * @param skillName    the display name of the skill
 * @param title        the title of the mini-project
 * @param statement    what to build, the requirements and what to deliver
 * @param rubric       the criteria it will be evaluated with
 * @param passingScore the minimum score (out of 100) to pass
 * @param createdAt    when it was generated (UTC)
 * @param dueAt        the deadline to deliver the repository (UTC)
 */
public record MiniProjectResource(
        int id,
        int pathNodeId,
        String skillTag,
        String skillName,
        String title,
        String statement,
        List<RubricCriterionResource> rubric,
        int passingScore,
        Instant createdAt,
        Instant dueAt) {
}
