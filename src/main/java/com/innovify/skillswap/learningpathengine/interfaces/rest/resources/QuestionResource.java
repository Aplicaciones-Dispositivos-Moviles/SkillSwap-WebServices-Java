package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import java.util.List;

/**
 * A question of an assessment as the student sees it. The correct answer is deliberately absent: it never
 * leaves the server.
 *
 * @param question the question text
 * @param answers  the four answer options, in a fixed order
 */
public record QuestionResource(String question, List<String> answers) {
}
