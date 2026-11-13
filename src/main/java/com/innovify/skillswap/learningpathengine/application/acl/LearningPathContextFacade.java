package com.innovify.skillswap.learningpathengine.application.acl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption facade through which other bounded contexts use Learning Path Engine, without depending on
 * its aggregates or repositories.
 */
public interface LearningPathContextFacade {

    /** The blueprint with the data needed to grade an attempt, or empty when it does not exist. */
    Optional<BlueprintView> getBlueprint(int blueprintId);

    /** The mini-project with the data needed to receive its delivery, or empty when it does not exist. */
    Optional<MiniProjectView> getMiniProject(int miniProjectId);

    /** The statement and the rubric a delivery of the mini-project is evaluated with, or empty when it does not exist. */
    Optional<MiniProjectRubricView> getMiniProjectRubric(int miniProjectId);

    /** Completes the node, unlocking the ones that depended on it. A persistence failure is reported as FAILED. */
    NodeCompletionOutcome completeNode(int pathNodeId);

    /**
     * Takes a completed node back to available, so the student demonstrates the skill again. A node completed with a
     * validated certificate is not reopened. A persistence failure is reported as FAILED.
     */
    NodeReopenOutcome reopenNode(int pathNodeId);

    /** The completion of the path with its skills, or empty when it does not exist. */
    Optional<PathCompletionView> getPathCompletion(int pathId);

    /** Whether the student has a completed node for the skill in any of their paths. */
    boolean hasCompletedSkill(int studentId, String skillTag);

    /**
     * The skills most asked for: for each skill of the goal of the paths created from {@code from} (inclusive) until
     * {@code to} (exclusive), how many paths asked for it. The most requested first, ties by tag. Empty for an empty
     * period or a limit under 1.
     */
    List<SkillDemandView> getMostRequestedSkills(Instant from, Instant to, int limit);
}
