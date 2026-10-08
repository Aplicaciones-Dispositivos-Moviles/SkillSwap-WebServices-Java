package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.shared.application.Result;

/** Learning path command service interface. */
public interface LearningPathCommandService {

    /** Interprets the goal, computes the skill gap and creates the path. */
    Result<LearningPath> handle(DeclareGoalCommand command);

    /** Completes a node, issued once the student approved the node's assessment. */
    Result<LearningPath> handle(CompletePathNodeCommand command);

    /**
     * Returns the student's latest path after linking the certificates uploaded since it was created. Linking
     * is best effort and never fails the read.
     */
    Result<LearningPath> handle(RefreshCertificateLinksCommand command);
}
