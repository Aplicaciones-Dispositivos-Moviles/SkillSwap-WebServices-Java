package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.EvaluateMiniProjectSubmissionCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitMiniProjectCommand;
import com.innovify.skillswap.shared.application.Result;

/** Mini-project submission command service interface. */
public interface MiniProjectSubmissionCommandService {

    /**
     * A student delivers the repository of the latest mini-project of an available node, before its deadline (US19
     * escenario 2). The delivery is saved as Pending and the {@code MiniProjectSubmitted} event is published once it
     * is committed; its preliminary evaluation reacts to that event. Fails with MINI_PROJECT_NOT_FOUND,
     * NOT_MINI_PROJECT_OWNER, INVALID_REPOSITORY_URL, NODE_NOT_AVAILABLE, MINI_PROJECT_OUTDATED, MINI_PROJECT_EXPIRED,
     * OPEN_CASE_ALREADY_EXISTS or MINI_PROJECT_ALREADY_SUBMITTED, or with DATABASE_ERROR / INTERNAL_SERVER_ERROR when
     * the write fails.
     */
    Result<MiniProjectSubmission> handle(SubmitMiniProjectCommand command);

    /**
     * The preliminary evaluation of a delivery (US19 escenarios 2 y 3). The AI grades the repository against the
     * rubric of the mini-project. When the score reaches the passing score of the rubric with a confidence that is not
     * low, the delivery is approved and its node completed, with no verifier. Otherwise (or when the AI cannot
     * evaluate it) a mini-project verification case is opened and assigned like the case of a failed quiz, using one of
     * the monthly escalations of the plan; with none left the delivery is kept without review. Fails with
     * MINI_PROJECT_SUBMISSION_NOT_FOUND, MINI_PROJECT_ALREADY_EVALUATED, OPEN_CASE_ALREADY_EXISTS (the node got another
     * case after the delivery), NODE_NOT_AVAILABLE, or DATABASE_ERROR / INTERNAL_SERVER_ERROR; the delivery then stays
     * Pending.
     */
    Result<MiniProjectEvaluationOutcome> handle(EvaluateMiniProjectSubmissionCommand command);
}
