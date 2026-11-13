package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReviewFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitDemonstrationVideoCommand;
import com.innovify.skillswap.shared.application.Result;

/** The video of a final demonstration and its review by the verifier (US29). */
public interface DemonstrationEvaluationCommandService {

    /**
     * Stores the video of the demonstration (MP4, WebM or QuickTime, up to 25 MB, whose real content matches the
     * declared type). A video can be replaced until the verifier reviews it.
     */
    Result<FinalDemonstration> handle(SubmitDemonstrationVideoCommand command);

    /**
     * The assigned verifier scores the video with the rubric: 70 or more approves it, less rejects it and needs a
     * feedback. {@code FinalDemonstrationReviewed} is published once it is committed.
     */
    Result<FinalDemonstration> handle(ReviewFinalDemonstrationCommand command);
}
