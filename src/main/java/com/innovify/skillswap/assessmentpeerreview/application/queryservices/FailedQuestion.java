package com.innovify.skillswap.assessmentpeerreview.application.queryservices;

import java.util.List;

/**
 * A question the student answered incorrectly. It never carries the correct answer.
 *
 * @param position       the position of the question in the assessment, starting at 1
 * @param text           the question
 * @param answers        the options
 * @param selectedAnswer the index of the option the student chose
 */
public record FailedQuestion(int position, String text, List<String> answers, int selectedAnswer) {
}
