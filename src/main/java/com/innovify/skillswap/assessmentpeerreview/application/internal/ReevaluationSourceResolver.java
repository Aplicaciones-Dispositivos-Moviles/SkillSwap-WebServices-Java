package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import java.util.Optional;

/**
 * Finds what a reevaluation refers to: the student, the node of the path and the skill of a passed attempt (through
 * its blueprint) or of an approved case. Both the command that orders a reevaluation and the query that shows the
 * student their reevaluations read the source this way.
 */
public class ReevaluationSourceResolver {

    private final AssessmentAttemptRepository attemptRepository;
    private final VerificationCaseRepository caseRepository;
    private final LearningPathContextFacade learningPathFacade;

    public ReevaluationSourceResolver(AssessmentAttemptRepository attemptRepository,
                                      VerificationCaseRepository caseRepository,
                                      LearningPathContextFacade learningPathFacade) {
        this.attemptRepository = attemptRepository;
        this.caseRepository = caseRepository;
        this.learningPathFacade = learningPathFacade;
    }

    /** The attempt or the case the reevaluation refers to, or why it cannot be found. */
    public Source resolve(ReevaluationSourceType sourceType, int sourceId) {
        return sourceType == ReevaluationSourceType.ATTEMPT ? sourceOfAttempt(sourceId) : sourceOfCase(sourceId);
    }

    private Source sourceOfAttempt(int attemptId) {
        Optional<AssessmentAttempt> attempt = attemptRepository.findById(attemptId);
        if (attempt.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.ATTEMPT_NOT_FOUND);
        }
        Optional<BlueprintView> blueprint = learningPathFacade.getBlueprint(attempt.get().getBlueprintId());
        if (blueprint.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.BLUEPRINT_NOT_FOUND);
        }
        String skillTag = attempt.get().getSkillTag() != null
                ? attempt.get().getSkillTag()
                : blueprint.get().skillTag();
        return new Source(attempt.get().getStudentId(), blueprint.get().pathNodeId(), skillTag,
                attempt.get().isPassed(), null);
    }

    private Source sourceOfCase(int caseId) {
        Optional<VerificationCase> found = caseRepository.findById(caseId);
        if (found.isEmpty()) {
            return Source.failed(AssessmentPeerReviewError.CASE_NOT_FOUND);
        }
        return new Source(found.get().getStudentId(), found.get().getPathNodeId(), found.get().getSkillTag(),
                found.get().canBeReevaluated(), null);
    }

    /**
     * What the reevaluation refers to.
     *
     * @param studentId   the student of the result
     * @param pathNodeId  the node of the path the result completed
     * @param skillTag    the skill that was assessed
     * @param reevaluable whether a reevaluation can be ordered on it now: the attempt passed or the case is approved
     * @param error       why the result cannot be found; null when it was found
     */
    public record Source(int studentId, int pathNodeId, String skillTag, boolean reevaluable,
                         AssessmentPeerReviewError error) {

        static Source failed(AssessmentPeerReviewError error) {
            return new Source(0, 0, null, false, error);
        }
    }
}
