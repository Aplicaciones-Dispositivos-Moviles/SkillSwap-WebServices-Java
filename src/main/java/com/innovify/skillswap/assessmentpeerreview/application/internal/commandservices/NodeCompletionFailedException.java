package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;

/**
 * Thrown inside a transaction when Learning Path Engine could not complete the node, so everything saved so
 * far is rolled back.
 */
final class NodeCompletionFailedException extends RuntimeException {

    private final transient NodeCompletionOutcome outcome;

    NodeCompletionFailedException(NodeCompletionOutcome outcome) {
        super("The node could not be completed.");
        this.outcome = outcome;
    }

    NodeCompletionOutcome outcome() {
        return outcome;
    }
}
