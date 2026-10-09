package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.shared.application.Result;

/** Assessment attempt command service interface. */
public interface AssessmentAttemptCommandService {

    /**
     * Grades the answers on the server. An approved attempt completes the node; otherwise a verification case
     * is opened, with the review deadline of the plan of the student, and assigned to a verifier when one is
     * available. When the student already used the escalations of the month their plan allows, no case is opened:
     * the attempt is still recorded and the outcome says which limit was reached.
     */
    Result<SubmitAssessmentAttemptOutcome> handle(SubmitAssessmentAttemptCommand command);
}
