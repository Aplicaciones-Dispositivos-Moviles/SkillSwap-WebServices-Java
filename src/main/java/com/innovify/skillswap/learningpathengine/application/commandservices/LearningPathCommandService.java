package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.CompletePathNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.DeclareGoalCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.EnforcePlanLimitsCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.LinkCertificateToNodeCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.PauseLearningPathCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RecognizeValidatedCertificateCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.RefreshCertificateLinksCommand;
import com.innovify.skillswap.learningpathengine.domain.model.commands.ResumeLearningPathCommand;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;

/** Learning path command service interface. */
public interface LearningPathCommandService {

    /**
     * Interprets the goal, computes the skill gap and creates the active path, if the plan of the student allows
     * another active path and another path in total (PLAN_LIMIT_REACHED otherwise). The skills covered by a
     * certificate the student already had validated are completed and linked to it.
     */
    Result<LearningPath> handle(DeclareGoalCommand command);

    /**
     * Associates a certificate of the student with a node of their path, when the certificate covers the skill
     * of the node (affinity at least {@code SkillAffinity.COVERAGE_THRESHOLD}). Otherwise fails with
     * CERTIFICATE_SKILL_MISMATCH, whose details list the nodes of the path the certificate does correspond to.
     * Fails also with NODE_NOT_FOUND, NOT_PATH_OWNER, NODE_ALREADY_COMPLETED, CERTIFICATE_NOT_FOUND,
     * NOT_CERTIFICATE_OWNER or CERTIFICATE_NOT_VERIFIED.
     */
    Result<CertificateLinkOutcome> handle(LinkCertificateToNodeCommand command);

    /**
     * A certificate of the student was validated: in every path that is not completed, the pending nodes whose
     * skill it covers are completed and linked to it, and the nodes they unlock become available. Completed nodes
     * are kept as they are. Returns the paths that changed.
     */
    Result<List<LearningPath>> handle(RecognizeValidatedCertificateCommand command);

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
