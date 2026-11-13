package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * A certificate associated with a node because it covers the skill of the node.
 *
 * @param pathNodeId        the node
 * @param certificateId     the certificate, now linked to the node
 * @param affinity          affinity between the certificate and the skill of the node (0 to 1)
 * @param threshold         the minimum affinity for a certificate to cover a skill
 * @param assessmentEnabled whether the practical assessment of the node can be requested now (the node is
 *                          available and the path active); a locked node needs its prerequisites first
 * @param node              the node with its new state
 */
public record CertificateLinkResource(
        int pathNodeId,
        int certificateId,
        double affinity,
        double threshold,
        boolean assessmentEnabled,
        PathNodeResource node) {
}
