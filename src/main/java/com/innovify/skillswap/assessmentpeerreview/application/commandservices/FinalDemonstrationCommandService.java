package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CancelFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.RescheduleFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ScheduleFinalDemonstrationCommand;
import com.innovify.skillswap.shared.application.Result;

/** Final demonstration command service interface (US28). */
public interface FinalDemonstrationCommandService {

    /**
     * Schedules the final demonstration of a completed path of the student in a moment at least 24 hours ahead and
     * assigns a verifier enabled in a skill of the path.
     */
    Result<FinalDemonstration> handle(ScheduleFinalDemonstrationCommand command);

    /** Moves a scheduled demonstration to another moment, at least 24 hours ahead. */
    Result<FinalDemonstration> handle(RescheduleFinalDemonstrationCommand command);

    /** Cancels a scheduled demonstration, so the path can schedule another one. */
    Result<FinalDemonstration> handle(CancelFinalDemonstrationCommand command);
}
