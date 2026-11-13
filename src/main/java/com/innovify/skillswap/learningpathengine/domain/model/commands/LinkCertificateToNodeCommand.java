package com.innovify.skillswap.learningpathengine.domain.model.commands;

/**
 * The student associates one of their certificates with a node of their path, as evidence of its skill.
 *
 * @param pathNodeId    the node
 * @param studentId     the authenticated student (never taken from the request body)
 * @param certificateId the certificate to associate
 */
public record LinkCertificateToNodeCommand(int pathNodeId, int studentId, int certificateId) {
}
