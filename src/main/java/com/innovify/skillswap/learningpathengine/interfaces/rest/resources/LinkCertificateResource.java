package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

/**
 * Resource for associating one of the student's certificates with a node of their path.
 *
 * @param certificateId a certificate of the authenticated student that passed the verification
 */
public record LinkCertificateResource(Integer certificateId) {
}
