package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.util.List;

/**
 * A question the student got wrong, with what they chose. The correct answer never leaves the server.
 *
 * @param position       the position in the assessment, starting at 1
 * @param question       the question
 * @param answers        the options
 * @param selectedAnswer the index of the option the student chose
 */
public record FailedQuestionResource(int position, String question, List<String> answers, int selectedAnswer) {
}
