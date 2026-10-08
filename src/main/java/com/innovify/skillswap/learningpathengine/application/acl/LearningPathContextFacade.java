package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.Optional;

/**
 * Anti-corruption facade through which other bounded contexts use Learning Path Engine, without depending on
 * its aggregates or repositories.
 */
public interface LearningPathContextFacade {

    /** The blueprint with the data needed to grade an attempt, or empty when it does not exist. */
    Optional<BlueprintView> getBlueprint(int blueprintId);

    /** Completes the node, unlocking the ones that depended on it. A persistence failure is reported as FAILED. */
    NodeCompletionOutcome completeNode(int pathNodeId);

    /** Whether the student has a completed node for the skill in any of their paths. */
    boolean hasCompletedSkill(int studentId, String skillTag);
}
