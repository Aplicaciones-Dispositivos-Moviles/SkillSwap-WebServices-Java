package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.util.List;

/**
 * The answers of a student to an assessment.
 *
 * @param blueprintId     the assessment that was answered
 * @param selectedAnswers one option index (0 to 3) per question, in the order of the assessment
 */
public record SubmitAssessmentAttemptResource(Integer blueprintId, List<Integer> selectedAnswers) {
}
