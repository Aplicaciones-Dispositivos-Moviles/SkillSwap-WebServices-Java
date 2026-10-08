package com.innovify.skillswap.learningpathengine.application.acl;

import java.util.List;

/**
 * A question of a blueprint, as seen by other bounded contexts. It includes the correct answer, so it must
 * never be mapped to a resource.
 */
public record BlueprintQuestionView(String text, List<String> answers, int correctAnswer) {
}
