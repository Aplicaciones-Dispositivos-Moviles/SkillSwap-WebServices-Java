package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.MiniProjectBrief;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.services.MiniProjectEvaluationService;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers a configured evaluation, or fails when an exception is set. By default the evaluation does not reach the
 * passing score of 70 ({@link #NOT_ENOUGH}), so a delivery goes to a verifier as before the AI evaluated it.
 */
public class FakeMiniProjectEvaluationService implements MiniProjectEvaluationService {

    /** Below the passing score of 70 with a good confidence: a verifier must review it. */
    public static final PreliminaryEvaluation NOT_ENOUGH =
            new PreliminaryEvaluation(55, 80, "The README does not explain how to run the project.");
    /** Above the passing score with a good confidence: the AI approves it. */
    public static final PreliminaryEvaluation ENOUGH = new PreliminaryEvaluation(85, 90, "Well structured and tested.");
    /** Above the passing score but with a low confidence: a verifier must review it. */
    public static final PreliminaryEvaluation UNSURE = new PreliminaryEvaluation(90, 40, "Only part was readable.");

    /** A request the fake received. */
    public record Request(MiniProjectBrief brief, String repositoryUrl) {
    }

    private final List<Request> requests = new ArrayList<>();
    private PreliminaryEvaluation evaluation = NOT_ENOUGH;
    private RuntimeException failure;

    public List<Request> requests() {
        return requests;
    }

    /** The evaluation every following call answers. */
    public void returnEvaluation(PreliminaryEvaluation evaluation) {
        this.evaluation = evaluation;
        this.failure = null;
    }

    /** Makes every following call throw the given exception, as when Gemini fails. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** Forgets the calls and goes back to {@link #NOT_ENOUGH}. */
    public void reset() {
        requests.clear();
        evaluation = NOT_ENOUGH;
        failure = null;
    }

    @Override
    public PreliminaryEvaluation evaluate(MiniProjectBrief brief, String repositoryUrl) {
        requests.add(new Request(brief, repositoryUrl));
        if (failure != null) {
            throw failure;
        }
        return evaluation;
    }
}
