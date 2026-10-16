package com.innovify.skillswap.assessmentpeerreview.domain.model.commands;

/**
 * A student delivers the repository of a mini-project.
 *
 * @param miniProjectId the mini-project that is delivered
 * @param studentId     the student (the authenticated user)
 * @param repositoryUrl the link to the repository (http or https, up to 500 characters)
 */
public record SubmitMiniProjectCommand(int miniProjectId, int studentId, String repositoryUrl) {
}
