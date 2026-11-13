package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * A question of a blueprint, as seen by other bounded contexts. It includes the correct answer, so it must
 * never be mapped to a resource.
 *
 * @param subTopic the sub-topic the question evaluates; null when it has none
 */
public record BlueprintQuestionView(String text, List<String> answers, int correctAnswer, String subTopic) {

    /** A question without a sub-topic. */
    public BlueprintQuestionView(String text, List<String> answers, int correctAnswer) {
        this(text, answers, correctAnswer, null);
    }
}
