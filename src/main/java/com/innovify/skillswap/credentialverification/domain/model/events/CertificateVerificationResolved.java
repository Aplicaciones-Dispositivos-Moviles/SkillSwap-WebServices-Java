package com.innovify.skillswap.credentialverification.domain.model.events;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.VerificationStatus;
import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A certificate reached a final verification status: {@link VerificationStatus#VERIFIED} or
 * {@link VerificationStatus#REJECTED}.
 *
 * @param certificateId   the certificate
 * @param ownerId         the student who uploaded it
 * @param status          the final status
 * @param courseName      the course read from the document (may be null)
 * @param rejectionReason why it was rejected; null when it was verified or no reason was given
 */
public record CertificateVerificationResolved(int certificateId, int ownerId, VerificationStatus status,
                                              String courseName, String rejectionReason) implements DomainEvent {
}
