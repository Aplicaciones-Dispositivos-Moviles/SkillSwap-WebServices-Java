package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Learning path resource for the REST API.
 *
 * @param id             the unique identifier of the path
 * @param studentId      the student who owns the path
 * @param goal           the goal as the student wrote it
 * @param goalSkillTags  the skills of the taxonomy the goal was interpreted as
 * @param status         Active, Paused (keeps its progress; resume it to continue) or Completed
 * @param nodes          the nodes, in prerequisite order
 * @param createdAt      when the path was created (UTC)
 * @param updatedAt      when the path last changed (UTC)
 * @param lastProgressAt when the student last advanced on it (UTC); after a downgrade the most recent stays active
 */
public record LearningPathResource(
        int id,
        int studentId,
        String goal,
        List<String> goalSkillTags,
        @Schema(allowableValues = {"Active", "Paused", "Completed"}) String status,
        List<PathNodeResource> nodes,
        Instant createdAt,
        Instant updatedAt,
        Instant lastProgressAt) {
}
