package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.shared.application.Result;

/** Mini-project submission command service interface. */
public interface MiniProjectSubmissionCommandService {

    /**
     * A student delivers the repository of the latest mini-project of an available node, before its deadline (US19).
     * The delivery is saved and, in the same transaction, the verification case is opened and assigned when a verifier
     * is available (it stays pending otherwise). When the student used all the monthly escalations of the plan the
     * delivery is saved anyway and no case is opened. The {@code MiniProjectSubmitted} event is published afterwards.
     * Fails with MINI_PROJECT_NOT_FOUND, NOT_MINI_PROJECT_OWNER, INVALID_REPOSITORY_URL, NODE_NOT_AVAILABLE,
     * MINI_PROJECT_OUTDATED, MINI_PROJECT_EXPIRED, OPEN_CASE_ALREADY_EXISTS or MINI_PROJECT_ALREADY_SUBMITTED, or
     * with DATABASE_ERROR / INTERNAL_SERVER_ERROR when the write fails.
     */
    Result<SubmitMiniProjectOutcome> handle(SubmitMiniProjectCommand command);
}
