package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;

/**
 * A certificate associated with a node because it covers its skill.
 *
 * @param path          the path, saved
 * @param pathNodeId    the node the certificate is now linked to
 * @param certificateId the certificate
 * @param affinity      the affinity between the certificate and the skill of the node (at least the threshold)
 */
public record CertificateLinkOutcome(LearningPath path, int pathNodeId, int certificateId, double affinity) {
}
