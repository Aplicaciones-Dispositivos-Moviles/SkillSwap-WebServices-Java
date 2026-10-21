package com.innovify.skillswap.learningpathengine.application.acl;

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

    /** Whether the student has a completed node for the skill in any of their paths. */
    boolean hasCompletedSkill(int studentId, String skillTag);
}
