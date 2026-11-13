package com.innovify.skillswap.learningpathengine.application.acl;

/** Outcome of asking Learning Path Engine to reopen a completed node. */
public enum NodeReopenOutcome {
    REOPENED,
    NODE_NOT_FOUND,
    NOT_COMPLETED,
    COMPLETED_BY_CERTIFICATE,
    FAILED
}
