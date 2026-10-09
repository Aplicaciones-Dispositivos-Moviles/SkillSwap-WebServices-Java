package com.innovify.skillswap.credentialverification.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.time.Instant;

/**
 * A verifier confirmed that a certificate is authentic ("Certificado verificado"): it is now validated evidence of
 * the skills it covers. Learning Path Engine reacts by recognizing those skills in the paths of the student.
 *
 * @param certificateId the certificate
 * @param ownerId       the student who owns it
 * @param verifiedAt    when it was verified
 */
public record CertificateVerified(int certificateId, int ownerId, Instant verifiedAt) implements DomainEvent {
}
