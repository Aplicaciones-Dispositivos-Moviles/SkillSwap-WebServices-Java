package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

/**
 * The delivery of a mini-project.
 *
 * @param repositoryUrl the link to the repository of the student (http or https, up to 500 characters)
 */
public record SubmitMiniProjectResource(String repositoryUrl) {
}
