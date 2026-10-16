package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The owner of a path asks for the AI-generated mini-project of an available node. While the latest one can still be
 * delivered it is returned again, unless the student asks to {@code regenerate} it (to try again after a rejection).
 */
public record GenerateMiniProjectCommand(int pathNodeId, int studentId, boolean regenerate) {

    public GenerateMiniProjectCommand(int pathNodeId, int studentId) {
        this(pathNodeId, studentId, false);
    }
}
