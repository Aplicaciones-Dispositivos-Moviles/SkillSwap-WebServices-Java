package com.innovify.skillswap.assessmentpeerreview.interfaces.rest.resources;

import java.time.Instant;

/**
 * The delivery of a mini-project.
 *
 * @param id            the unique identifier of the delivery
 * @param miniProjectId the mini-project that was delivered
 * @param pathNodeId    the node it assesses
 * @param skillTag      the skill being demonstrated
 * @param repositoryUrl the link to the repository
 * @param submittedAt   when it was delivered (UTC)
 */
public record MiniProjectSubmissionResource(int id, int miniProjectId, int pathNodeId, String skillTag,
                                            String repositoryUrl, Instant submittedAt) {
}
