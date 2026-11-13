package com.innovify.skillswap.learningpathengine.application.acl;

/** Outcome of asking Learning Path Engine to complete a node. */
public enum NodeCompletionOutcome {
    COMPLETED,
    NODE_NOT_FOUND,
    NODE_LOCKED,
    ALREADY_COMPLETED,
    FAILED
}
