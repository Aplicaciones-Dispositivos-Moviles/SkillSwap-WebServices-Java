package com.innovify.skillswap.moderationdisputes.interfaces.rest.resources;

/**
 * A dispute with the evidence to decide it.
 *
 * @param dispute     the dispute
 * @param certificate the certificate under review; null when it no longer exists
 */
public record DisputeEvidenceResource(DisputeResource dispute, CertificateEvidenceResource certificate) {
}
