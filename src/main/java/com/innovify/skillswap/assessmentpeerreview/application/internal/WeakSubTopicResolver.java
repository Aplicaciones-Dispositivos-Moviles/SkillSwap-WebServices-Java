package com.innovify.skillswap.assessmentpeerreview.application.internal;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.services.QuestionOutcome;
import com.innovify.skillswap.assessmentpeerreview.domain.services.WeakSubTopicAnalyzer;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Finds the sub-topic the student should reinforce in an attempt. It matches the answers of the attempt with the
 * sub-topics of the questions of the blueprint, and leaves the choice to {@link WeakSubTopicAnalyzer}.
 */
public final class WeakSubTopicResolver {

    private WeakSubTopicResolver() {
    }

    /**
     * @param attempt   the graded attempt
     * @param questions the questions of its blueprint, in the order they were answered
     * @return the sub-topic to reinforce, or null when there is no wrong answer in a question with a sub-topic
     */
    public static String resolve(AssessmentAttempt attempt, List<BlueprintQuestionView> questions) {
        List<Integer> correctAnswers = questions.stream().map(BlueprintQuestionView::correctAnswer).toList();
        Set<Integer> incorrect = new HashSet<>(attempt.incorrectQuestionIndexes(correctAnswers));

        List<QuestionOutcome> outcomes = IntStream.range(0, questions.size())
                .mapToObj(index -> new QuestionOutcome(questions.get(index).subTopic(), !incorrect.contains(index)))
                .toList();
        return WeakSubTopicAnalyzer.find(outcomes).orElse(null);
    }
}
