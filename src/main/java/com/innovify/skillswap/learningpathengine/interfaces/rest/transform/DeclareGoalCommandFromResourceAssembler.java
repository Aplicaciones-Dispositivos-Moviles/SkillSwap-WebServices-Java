package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.DeclareGoalResource;

public final class DeclareGoalCommandFromResourceAssembler {

    private DeclareGoalCommandFromResourceAssembler() {
    }

    /** The student is always the authenticated user, never a value from the request body. */
    public static DeclareGoalCommand toCommandFromResource(DeclareGoalResource resource, int studentId) {
        return new DeclareGoalCommand(studentId, resource == null || resource.goal() == null ? "" : resource.goal(),
                resource != null && Boolean.TRUE.equals(resource.advanced()));
    }
}
