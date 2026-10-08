package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitAssessmentAttemptCommand;
import com.innovify.skillswap.shared.application.Result;

/** Assessment attempt command service interface. */
public interface AssessmentAttemptCommandService {

    /**
     * Grades the answers on the server. An approved attempt completes the node; otherwise a verification case
     * is opened and assigned to a verifier when one is available.
     */
    Result<SubmitAssessmentAttemptOutcome> handle(SubmitAssessmentAttemptCommand command);
}
