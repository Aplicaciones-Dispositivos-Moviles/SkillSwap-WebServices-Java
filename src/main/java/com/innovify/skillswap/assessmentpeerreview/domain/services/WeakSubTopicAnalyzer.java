package com.innovify.skillswap.assessmentpeerreview.domain.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the sub-topic where the student failed the most in an assessment, so they can reinforce only that part.
 * The sub-topic with the most wrong answers wins; on a tie, the one the student failed first. Questions without a
 * sub-topic are ignored, and the ones answered correctly do not count: with no wrong answer there is nothing to
 * reinforce.
 */
public final class WeakSubTopicAnalyzer {

    private WeakSubTopicAnalyzer() {
    }

    /**
     * @param outcomes the result of each question, in the order of the assessment
     * @return the weakest sub-topic, or empty when there is no wrong answer with a sub-topic
     */
    public static Optional<String> find(List<QuestionOutcome> outcomes) {
        if (outcomes == null) {
            return Optional.empty();
        }
        // Only the wrong answers are counted, so the insertion order is the order of the first failure of each
        // sub-topic, which breaks the ties.
        Map<String, Integer> failures = new LinkedHashMap<>();
        for (QuestionOutcome outcome : outcomes) {
            if (outcome.subTopic() == null || outcome.correct()) {
                continue;
            }
            failures.merge(outcome.subTopic(), 1, Integer::sum);
        }

        String weakest = null;
        int most = 0;
        for (Map.Entry<String, Integer> entry : failures.entrySet()) {
            if (entry.getValue() > most) {
                weakest = entry.getKey();
                most = entry.getValue();
            }
        }
        return Optional.ofNullable(weakest);
    }
}
