package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.EnforcePlanLimitsCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;

/** Learning path command service interface. */
public interface LearningPathCommandService {

    /**
     * Interprets the goal, computes the skill gap and creates the active path, if the plan of the student allows
     * another active path and another path in total (PLAN_LIMIT_REACHED otherwise).
     */
    Result<LearningPath> handle(DeclareGoalCommand command);

    /** Pauses an active path of the student. Fails with PATH_NOT_FOUND, NOT_PATH_OWNER or PATH_NOT_ACTIVE. */
    Result<LearningPath> handle(PauseLearningPathCommand command);

    /**
     * Resumes a paused path of the student when the plan allows another active path. Fails with PATH_NOT_FOUND,
     * NOT_PATH_OWNER, PATH_NOT_PAUSED or PLAN_LIMIT_REACHED.
     */
    Result<LearningPath> handle(ResumeLearningPathCommand command);

    /**
     * Keeps active only as many paths as the plan allows, the ones with the most recent progress, and pauses the
     * others. Returns the paths it paused.
     */
    Result<List<LearningPath>> handle(EnforcePlanLimitsCommand command);

    /** Completes a node, issued once the student approved the node's assessment. */
    Result<LearningPath> handle(CompletePathNodeCommand command);

    /**
     * Returns the student's latest path after linking the certificates uploaded since it was created. Linking
     * is best effort and never fails the read.
     */
    Result<LearningPath> handle(RefreshCertificateLinksCommand command);
}
