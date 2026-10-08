package com.innovify.skillswap.learningpathengine.domain.model;

/** Errors of the Learning Path Engine context. The API code is the PascalCase name (InvalidGoal...). */
public enum LearningPathError {
    NONE,
    INVALID_GOAL,
    GOAL_NOT_INTERPRETABLE,
    GOAL_ALREADY_ACHIEVED,
    ACTIVE_PATH_ALREADY_EXISTS,
    PATH_NOT_FOUND,
    NOT_PATH_OWNER,
    NODE_NOT_FOUND,
    NODE_LOCKED,
    NODE_ALREADY_COMPLETED,
    QUESTION_GENERATION_FAILED,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
