package com.innovify.skillswap.assessmentpeerreview.domain.services;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.MiniProjectBrief;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;

/**
 * Port of the preliminary evaluation of a mini-project delivery by the AI (US19 escenario 2). The adapter lives in
 * infrastructure (Gemini).
 */
public interface MiniProjectEvaluationService {

    /**
     * Grades the repository against the rubric of the mini-project.
     *
     * @return the score, the confidence and the feedback of the evaluation
     * @throws RuntimeException when the evaluation cannot be done (the provider fails or answers outside the contract);
     *                          the caller then treats the delivery as one with a low confidence
     */
    PreliminaryEvaluation evaluate(MiniProjectBrief brief, String repositoryUrl);
}
