package com.innovify.skillswap.learningpathengine.domain.model.commands;

/** The student declares a career goal in free text. */
public record DeclareGoalCommand(int studentId, String rawText) {
}
