package com.innovify.skillswap.learningpathengine.domain.model.queries;

/** Every learning path of a student, newest first. */
public record GetLearningPathsByStudentIdQuery(int studentId) {
}
