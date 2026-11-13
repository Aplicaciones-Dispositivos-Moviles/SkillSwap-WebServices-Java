package com.innovify.skillswap.assessmentpeerreview.domain.services;

/**
 * How the student did in one question of an assessment.
 *
 * @param subTopic the sub-topic the question evaluates, or null when it has none
 * @param correct  whether the student chose the correct answer
 */
public record QuestionOutcome(String subTopic, boolean correct) {
}
