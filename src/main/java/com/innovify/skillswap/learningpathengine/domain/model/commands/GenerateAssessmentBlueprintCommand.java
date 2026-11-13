package com.innovify.skillswap.learningpathengine.domain.model.commands;

/** The owner of a path asks for the AI-generated assessment of an available node. */
public record GenerateAssessmentBlueprintCommand(int pathNodeId, int studentId) {
}
